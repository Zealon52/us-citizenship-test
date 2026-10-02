package com.usctest.app.ui.recall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.usctest.app.data.AnswerRecord
import com.usctest.app.data.OfficialsRepository
import com.usctest.app.data.ProgressRepository
import com.usctest.app.data.QuestionRepository
import com.usctest.app.data.SettingsRepository
import com.usctest.app.data.local.AttemptMode
import com.usctest.app.data.local.InputMethod
import com.usctest.app.data.model.AcceptableAnswer
import com.usctest.app.data.model.AnswerType
import com.usctest.app.data.model.Question
import com.usctest.app.data.model.TestVersion
import com.usctest.app.domain.AnswerDisplay
import com.usctest.app.domain.AnswerDisplayFormatter
import com.usctest.app.domain.AnswerGrader
import com.usctest.app.domain.ExamFormat
import com.usctest.app.domain.ExamRules
import com.usctest.app.domain.StateAnswerResolver
import com.usctest.app.domain.TestVersionResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class RecallQuestionState(
    val question: Question,
    val resolvedAnswers: List<AcceptableAnswer>,
    val isPersonAnswer: Boolean,
)

data class RecallFeedback(val wasCorrect: Boolean, val display: AnswerDisplay)

data class RecallAnswerRecord(
    val question: Question,
    val submittedText: String,
    val wasCorrect: Boolean,
    val display: AnswerDisplay,
    val inputMethod: InputMethod,
)

data class RecallUiState(
    val isLoading: Boolean = true,
    val testVersion: TestVersion = TestVersion.V2008,
    val questions: List<RecallQuestionState> = emptyList(),
    val currentIndex: Int = 0,
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val inputText: String = "",
    val inputMethod: InputMethod = InputMethod.TYPED,
    val feedback: RecallFeedback? = null,
    val isFinished: Boolean = false,
    val passed: Boolean = false,
    val records: List<RecallAnswerRecord> = emptyList(),
) {
    val currentQuestion: RecallQuestionState? get() = questions.getOrNull(currentIndex)
}

class RecallModeViewModel(
    private val settingsRepository: SettingsRepository,
    private val questionRepository: QuestionRepository,
    private val officialsRepository: OfficialsRepository,
    private val progressRepository: ProgressRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecallUiState())
    val uiState: StateFlow<RecallUiState> = _uiState.asStateFlow()

    private var startedAtMillis = 0L

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val profile = settingsRepository.profile.first()
            val version = TestVersionResolver.resolve(settings, profile.filingDate)
            val rules = ExamFormat.rulesFor(version)

            val bank = questionRepository.getQuestions(version)
            val officials = settings.selectedStateCode?.let { officialsRepository.getStateOfficials(it) }

            // Bank is currently sample content (10 questions/version) — see Open Items in the
            // app plan. Drawing min(bank.size, maxQuestions) keeps this honest until the real
            // 100/128-question banks land.
            val drawn = bank.shuffled().take(minOf(rules.maxQuestions, bank.size))
            val questionStates = drawn.map { question ->
                RecallQuestionState(
                    question = question,
                    resolvedAnswers = StateAnswerResolver.resolveAcceptableAnswers(
                        question,
                        officials,
                        settings.selectedRepresentative,
                    ),
                    isPersonAnswer = question.answerType == AnswerType.PERSON,
                )
            }

            startedAtMillis = System.currentTimeMillis()
            _uiState.value = RecallUiState(isLoading = false, testVersion = version, questions = questionStates)
        }
    }

    fun updateInput(text: String) {
        val state = _uiState.value
        if (state.feedback != null) return
        val method = if (state.inputMethod == InputMethod.TYPED) InputMethod.TYPED else InputMethod.DICTATED_EDITED
        _uiState.value = state.copy(inputText = text, inputMethod = method)
    }

    /** Called with live/final text from [com.usctest.app.speech.SpeechRecognizerManager]. */
    fun onDictatedText(text: String) {
        if (_uiState.value.feedback != null) return
        _uiState.value = _uiState.value.copy(inputText = text, inputMethod = InputMethod.DICTATED)
    }

    fun submit() {
        val state = _uiState.value
        if (state.feedback != null) return
        val current = state.currentQuestion ?: return
        if (state.inputText.isBlank()) return

        val result = AnswerGrader.grade(
            responseText = state.inputText,
            acceptableAnswers = current.resolvedAnswers,
            answerMode = current.question.answerMode,
            requiredCount = current.question.requiredCount,
            isPersonAnswer = current.isPersonAnswer,
        )
        val display = AnswerDisplayFormatter.format(current.question, current.resolvedAnswers.map { it.canonical })
        val record = RecallAnswerRecord(current.question, state.inputText, result.isCorrect, display, state.inputMethod)

        _uiState.value = state.copy(
            feedback = RecallFeedback(result.isCorrect, display),
            correctCount = state.correctCount + if (result.isCorrect) 1 else 0,
            wrongCount = state.wrongCount + if (!result.isCorrect) 1 else 0,
            records = state.records + record,
        )
    }

    fun next() {
        val state = _uiState.value
        if (state.feedback == null) return
        val rules = ExamFormat.rulesFor(state.testVersion)

        val shouldStop = state.correctCount >= rules.passThreshold ||
            state.wrongCount >= rules.failThreshold ||
            state.currentIndex + 1 >= state.questions.size

        if (shouldStop) {
            finish(state, rules)
        } else {
            _uiState.value = state.copy(
                currentIndex = state.currentIndex + 1,
                inputText = "",
                inputMethod = InputMethod.TYPED,
                feedback = null,
            )
        }
    }

    private fun finish(state: RecallUiState, rules: ExamRules) {
        val passed = state.correctCount >= rules.passThreshold
        val endedEarly = state.currentIndex + 1 < state.questions.size
        _uiState.value = state.copy(isFinished = true, passed = passed)

        viewModelScope.launch {
            progressRepository.recordAttempt(
                testVersion = state.testVersion,
                mode = AttemptMode.RECALL,
                startedAt = startedAtMillis,
                finishedAt = System.currentTimeMillis(),
                passed = passed,
                endedEarly = endedEarly,
                answers = state.records.map {
                    AnswerRecord(
                        questionId = it.question.id,
                        wasCorrect = it.wasCorrect,
                        submittedText = it.submittedText,
                        inputMethod = it.inputMethod,
                    )
                },
            )
        }
    }
}
