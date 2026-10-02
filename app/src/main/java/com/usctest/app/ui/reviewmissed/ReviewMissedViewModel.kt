package com.usctest.app.ui.reviewmissed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.usctest.app.data.AnswerRecord
import com.usctest.app.data.OfficialsRepository
import com.usctest.app.data.ProgressRepository
import com.usctest.app.data.QuestionRepository
import com.usctest.app.data.SettingsRepository
import com.usctest.app.data.local.AttemptMode
import com.usctest.app.data.local.InputMethod
import com.usctest.app.data.local.MasteryLevel
import com.usctest.app.data.model.AcceptableAnswer
import com.usctest.app.data.model.AnswerMode
import com.usctest.app.data.model.AnswerType
import com.usctest.app.data.model.OfficialsData
import com.usctest.app.data.model.Question
import com.usctest.app.data.model.TestVersion
import com.usctest.app.domain.AnswerDisplay
import com.usctest.app.domain.AnswerDisplayFormatter
import com.usctest.app.domain.AnswerGrader
import com.usctest.app.domain.AnswerKeyRotation
import com.usctest.app.domain.DistractorProvider
import com.usctest.app.domain.StateAnswerResolver
import com.usctest.app.domain.TestVersionResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class ReviewFormat { RECALL, MCQ }

/** One MCQ option. `isCorrect` drives grading only — never shown to the user pre-submit. */
data class ReviewOption(val text: String, val isCorrect: Boolean)

data class ReviewQuestionState(
    val question: Question,
    val resolvedAnswers: List<AcceptableAnswer>,
    val isPersonAnswer: Boolean,
    val options: List<ReviewOption> = emptyList(),
    val isMultiSelect: Boolean = false,
    val requiredSelectionCount: Int = 1,
)

data class ReviewFeedback(
    val wasCorrect: Boolean,
    val display: AnswerDisplay? = null,
    val alsoAccepted: List<String> = emptyList(),
)

data class ReviewAnswerRecord(
    val question: Question,
    val wasCorrect: Boolean,
    val submittedText: String?,
    val display: AnswerDisplay?,
    val resolvedAnswers: List<AcceptableAnswer>,
)

data class ReviewMissedUiState(
    val isLoading: Boolean = true,
    val missedCount: Int = 0,
    val format: ReviewFormat? = null,
    val testVersion: TestVersion = TestVersion.V2008,
    val questions: List<ReviewQuestionState> = emptyList(),
    val currentIndex: Int = 0,
    val inputText: String = "",
    val selectedIndices: Set<Int> = emptySet(),
    val feedback: ReviewFeedback? = null,
    val isFinished: Boolean = false,
    val records: List<ReviewAnswerRecord> = emptyList(),
) {
    val currentQuestion: ReviewQuestionState? get() = questions.getOrNull(currentIndex)
    val correctCount: Int get() = records.count { it.wasCorrect }

    /** Picker is shown once loading is done, a session hasn't started, and there's something to review. */
    val showFormatPicker: Boolean get() = !isLoading && format == null && missedCount > 0
    val showEmptyState: Boolean get() = !isLoading && format == null && missedCount == 0
}

/**
 * A session drawn only from questions the user has answered wrong (weighted by miss count and
 * least-recently-seen, per [ProgressRepository.getMissedQuestionIds]), in either Recall or MCQ
 * format. Unlike Recall Mode / Practice Test, this is a review drill, not an exam simulation:
 * every missed question is reviewed once, with no official pass/fail threshold or early stop.
 */
class ReviewMissedViewModel(
    private val settingsRepository: SettingsRepository,
    private val questionRepository: QuestionRepository,
    private val officialsRepository: OfficialsRepository,
    private val progressRepository: ProgressRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReviewMissedUiState())
    val uiState: StateFlow<ReviewMissedUiState> = _uiState.asStateFlow()

    private var startedAtMillis = 0L
    private var missedIds: List<Int> = emptyList()
    private var bank: List<Question> = emptyList()
    private var officials: com.usctest.app.data.model.StateOfficials? = null
    private var officialsData: OfficialsData = OfficialsData(updatedAt = "", states = emptyList())
    private var selectedRepresentative: String? = null
    private var selectedStateCode: String? = null

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val profile = settingsRepository.profile.first()
            val version = TestVersionResolver.resolve(settings, profile.filingDate)

            bank = questionRepository.getQuestions(version)
            officials = settings.selectedStateCode?.let { officialsRepository.getStateOfficials(it) }
            officialsData = officialsRepository.getOfficialsData()
            selectedRepresentative = settings.selectedRepresentative
            selectedStateCode = settings.selectedStateCode
            missedIds = progressRepository.getMissedQuestionIds(version)

            _uiState.value = ReviewMissedUiState(
                isLoading = false,
                missedCount = missedIds.size,
                testVersion = version,
            )
        }
    }

    fun selectFormat(format: ReviewFormat) {
        val state = _uiState.value
        if (state.format != null) return

        viewModelScope.launch {
            val byId = bank.associateBy { it.id }
            val ordered = missedIds.mapNotNull { byId[it] }
            val statsById = progressRepository.getAllStats(state.testVersion).associateBy { it.questionId }

            val questionStates = ordered.map { question ->
                val resolvedAnswers = StateAnswerResolver.resolveAcceptableAnswers(
                    question,
                    officials,
                    selectedRepresentative,
                )
                if (format == ReviewFormat.MCQ) {
                    val stats = statsById[question.id]
                    val mastery = stats?.masteryLevel ?: MasteryLevel.NEW
                    val preferred = preferredDistractorsFor(question, officialsData)
                    buildMcqState(question, resolvedAnswers, mastery, preferred, stats?.timesSeen ?: 0)
                } else {
                    ReviewQuestionState(
                        question = question,
                        resolvedAnswers = resolvedAnswers,
                        isPersonAnswer = question.answerType == AnswerType.PERSON,
                    )
                }
            }

            startedAtMillis = System.currentTimeMillis()
            _uiState.value = state.copy(format = format, questions = questionStates)
        }
    }

    /** Mirrors [com.usctest.app.ui.practicetest.PracticeTestViewModel]'s preferred-distractor
     * logic: state-specific questions are better served by other states' real officials than by
     * whatever unrelated answerType the bank has lying around. */
    private fun preferredDistractorsFor(question: Question, officialsData: OfficialsData): List<String> {
        if (!question.isStateSpecific) return emptyList()
        val text = question.text.lowercase()
        val otherStates = officialsData.states.filterNot { it.stateCode.equals(selectedStateCode, ignoreCase = true) }
        val candidates = when {
            "senator" in text -> otherStates.flatMap { it.senators }
            "governor" in text -> otherStates.map { it.governor }
            "representative" in text -> otherStates.flatMap { it.representatives }
            else -> emptyList()
        }
        return candidates.filterNot { "varies by district" in it.lowercase() }
    }

    private fun buildMcqState(
        question: Question,
        resolvedAnswers: List<AcceptableAnswer>,
        mastery: MasteryLevel,
        preferredDistractors: List<String>,
        timesSeen: Int,
    ): ReviewQuestionState {
        val correctCanonicals = resolvedAnswers.map { it.canonical }

        return when (question.answerMode) {
            AnswerMode.ANY_OF -> {
                val distractors = DistractorProvider.distractorsFor(
                    question, resolvedAnswers, bank, mastery, preferredDistractors, required = TOTAL_OPTIONS - 1,
                )
                val keyed = AnswerKeyRotation.pick(correctCanonicals, timesSeen)
                val options = (listOf(ReviewOption(keyed, true)) + distractors.map { ReviewOption(it, false) })
                    .shuffled()
                ReviewQuestionState(question, resolvedAnswers, question.answerType == AnswerType.PERSON, options, isMultiSelect = false, requiredSelectionCount = 1)
            }
            AnswerMode.N_OF -> {
                val correctShown = correctCanonicals.shuffled()
                    .take(minOf(correctCanonicals.size, TOTAL_OPTIONS - 1))
                    .let { if (it.size < question.requiredCount) correctCanonicals.shuffled().take(question.requiredCount) else it }
                val distractorsNeeded = (TOTAL_OPTIONS - correctShown.size).coerceAtLeast(0)
                val distractors = DistractorProvider.distractorsFor(
                    question, resolvedAnswers, bank, mastery, preferredDistractors, required = distractorsNeeded,
                )
                val options = (correctShown.map { ReviewOption(it, true) } + distractors.map { ReviewOption(it, false) })
                    .shuffled()
                ReviewQuestionState(question, resolvedAnswers, question.answerType == AnswerType.PERSON, options, isMultiSelect = true, requiredSelectionCount = question.requiredCount)
            }
            AnswerMode.ALL_OF -> {
                val distractorsNeeded = (TOTAL_OPTIONS - correctCanonicals.size).coerceAtLeast(0)
                val distractors = DistractorProvider.distractorsFor(
                    question, resolvedAnswers, bank, mastery, preferredDistractors, required = distractorsNeeded,
                )
                val options = (correctCanonicals.map { ReviewOption(it, true) } + distractors.map { ReviewOption(it, false) })
                    .shuffled()
                ReviewQuestionState(question, resolvedAnswers, question.answerType == AnswerType.PERSON, options, isMultiSelect = true, requiredSelectionCount = correctCanonicals.size)
            }
        }
    }

    private companion object {
        const val TOTAL_OPTIONS = 4
    }

    fun updateInput(text: String) {
        if (_uiState.value.feedback != null) return
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun toggleOption(index: Int) {
        val state = _uiState.value
        if (state.feedback != null) return
        val current = state.currentQuestion ?: return

        val newSelection = if (!current.isMultiSelect) {
            setOf(index)
        } else {
            when {
                index in state.selectedIndices -> state.selectedIndices - index
                state.selectedIndices.size >= current.requiredSelectionCount -> state.selectedIndices
                else -> state.selectedIndices + index
            }
        }
        _uiState.value = state.copy(selectedIndices = newSelection)
    }

    fun submit() {
        val state = _uiState.value
        if (state.feedback != null) return
        val current = state.currentQuestion ?: return

        if (state.format == ReviewFormat.RECALL) {
            if (state.inputText.isBlank()) return
            val result = AnswerGrader.grade(
                responseText = state.inputText,
                acceptableAnswers = current.resolvedAnswers,
                answerMode = current.question.answerMode,
                requiredCount = current.question.requiredCount,
                isPersonAnswer = current.isPersonAnswer,
            )
            val display = AnswerDisplayFormatter.format(current.question, current.resolvedAnswers.map { it.canonical })
            _uiState.value = state.copy(
                feedback = ReviewFeedback(result.isCorrect, display = display),
                records = state.records + ReviewAnswerRecord(
                    current.question, result.isCorrect, state.inputText, display, current.resolvedAnswers,
                ),
            )
        } else {
            if (state.selectedIndices.isEmpty()) return
            val correctOptionsCount = current.options.count { it.isCorrect }
            val requiredMatchCount = if (current.question.answerMode == AnswerMode.ALL_OF) {
                correctOptionsCount
            } else {
                current.requiredSelectionCount
            }
            val wasCorrect = state.selectedIndices.size == requiredMatchCount &&
                state.selectedIndices.all { current.options[it].isCorrect }

            val shownCorrectTexts = current.options.filter { it.isCorrect }.map { it.text }
            val alsoAccepted = current.resolvedAnswers.map { it.canonical }.filterNot { it in shownCorrectTexts }

            _uiState.value = state.copy(
                feedback = ReviewFeedback(wasCorrect, alsoAccepted = alsoAccepted),
                records = state.records + ReviewAnswerRecord(
                    current.question, wasCorrect, submittedText = null, display = null, resolvedAnswers = current.resolvedAnswers,
                ),
            )
        }
    }

    fun next() {
        val state = _uiState.value
        if (state.feedback == null) return

        if (state.currentIndex + 1 >= state.questions.size) {
            finish(state)
        } else {
            _uiState.value = state.copy(
                currentIndex = state.currentIndex + 1,
                inputText = "",
                selectedIndices = emptySet(),
                feedback = null,
            )
        }
    }

    private fun finish(state: ReviewMissedUiState) {
        _uiState.value = state.copy(isFinished = true)

        val mode = if (state.format == ReviewFormat.RECALL) AttemptMode.RECALL else AttemptMode.MCQ
        viewModelScope.launch {
            progressRepository.recordAttempt(
                testVersion = state.testVersion,
                mode = mode,
                startedAt = startedAtMillis,
                finishedAt = System.currentTimeMillis(),
                passed = state.correctCount == state.records.size,
                endedEarly = false,
                answers = state.records.map {
                    AnswerRecord(
                        questionId = it.question.id,
                        wasCorrect = it.wasCorrect,
                        submittedText = it.submittedText,
                        inputMethod = InputMethod.TYPED,
                    )
                },
            )
        }
    }
}
