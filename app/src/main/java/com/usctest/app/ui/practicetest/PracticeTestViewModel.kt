package com.usctest.app.ui.practicetest

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
import com.usctest.app.data.model.OfficialsData
import com.usctest.app.data.model.Question
import com.usctest.app.data.model.TestVersion
import com.usctest.app.domain.AnswerKeyRotation
import com.usctest.app.domain.DistractorProvider
import com.usctest.app.domain.StateAnswerResolver
import com.usctest.app.domain.TestVersionResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** One selectable option. `isCorrect` drives grading only -- never shown to the user pre-submit. */
data class PracticeOption(val text: String, val isCorrect: Boolean)

data class PracticeQuestionState(
    val question: Question,
    val resolvedAnswers: List<AcceptableAnswer>,
    val options: List<PracticeOption>,
    val isMultiSelect: Boolean,
    val requiredSelectionCount: Int,
)

data class PracticeFeedback(val wasCorrect: Boolean, val alsoAccepted: List<String>)

data class PracticeAnswerRecord(
    val question: Question,
    val wasCorrect: Boolean,
    val resolvedAnswers: List<AcceptableAnswer>,
)

data class PracticeUiState(
    val isLoading: Boolean = true,
    val testVersion: TestVersion = TestVersion.V2008,
    val questions: List<PracticeQuestionState> = emptyList(),
    val currentIndex: Int = 0,
    val selectedIndices: Set<Int> = emptySet(),
    val feedback: PracticeFeedback? = null,
    val passingPercent: Int = 60,
    val isFinished: Boolean = false,
    val records: List<PracticeAnswerRecord> = emptyList(),
) {
    val currentQuestion: PracticeQuestionState? get() = questions.getOrNull(currentIndex)
    val correctCount: Int get() = records.count { it.wasCorrect }
    val scorePercent: Int get() = if (records.isEmpty()) 0 else (correctCount * 100) / records.size
    val passed: Boolean get() = scorePercent >= passingPercent
}

class PracticeTestViewModel(
    private val settingsRepository: SettingsRepository,
    private val questionRepository: QuestionRepository,
    private val officialsRepository: OfficialsRepository,
    private val progressRepository: ProgressRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PracticeUiState())
    val uiState: StateFlow<PracticeUiState> = _uiState.asStateFlow()

    private var startedAtMillis = 0L

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val profile = settingsRepository.profile.first()
            val version = TestVersionResolver.resolve(settings, profile.filingDate)

            val bank = questionRepository.getQuestions(version)
            val officials = settings.selectedStateCode?.let { officialsRepository.getStateOfficials(it) }
            val officialsData = officialsRepository.getOfficialsData()
            val statsById = progressRepository.getAllStats(version).associateBy { it.questionId }

            // Bank is currently sample content (10 questions/version) -- see Open Items in the
            // app plan. Drawing min(bank.size, configured count) keeps this honest until the
            // real 100/128-question banks land.
            val drawn = bank.shuffled().take(minOf(settings.practiceQuestionCount, bank.size))
            val questionStates = drawn.map { question ->
                val resolvedAnswers = StateAnswerResolver.resolveAcceptableAnswers(
                    question,
                    officials,
                    settings.selectedRepresentative,
                )
                val stats = statsById[question.id]
                val mastery = stats?.masteryLevel ?: MasteryLevel.NEW
                val preferred = preferredDistractorsFor(question, officialsData, settings.selectedStateCode)
                buildQuestionState(question, resolvedAnswers, bank, mastery, preferred, stats?.timesSeen ?: 0)
            }

            startedAtMillis = System.currentTimeMillis()
            _uiState.value = PracticeUiState(
                isLoading = false,
                testVersion = version,
                questions = questionStates,
                passingPercent = settings.practicePassingPercent,
            )
        }
    }

    /** State-specific questions (senator/governor/representative) are better served by other
     * states' real officials than by whatever unrelated answerType the sample bank has lying
     * around -- see [DistractorProvider]'s `preferredDistractors` param. */
    private fun preferredDistractorsFor(
        question: Question,
        officialsData: OfficialsData,
        ownStateCode: String?,
    ): List<String> {
        if (!question.isStateSpecific) return emptyList()
        val text = question.text.lowercase()
        val otherStates = officialsData.states.filterNot { it.stateCode.equals(ownStateCode, ignoreCase = true) }
        val candidates = when {
            "senator" in text -> otherStates.flatMap { it.senators }
            "governor" in text -> otherStates.map { it.governor }
            "representative" in text -> otherStates.flatMap { it.representatives }
            else -> emptyList()
        }
        // Filters the "(varies by district — see Settings to update)" placeholder some states
        // still carry for representatives -- see Open Items in the app plan.
        return candidates.filterNot { "varies by district" in it.lowercase() }
    }

    private fun buildQuestionState(
        question: Question,
        resolvedAnswers: List<AcceptableAnswer>,
        bank: List<Question>,
        mastery: MasteryLevel,
        preferredDistractors: List<String>,
        timesSeen: Int,
    ): PracticeQuestionState {
        val correctCanonicals = resolvedAnswers.map { it.canonical }

        return when (question.answerMode) {
            AnswerMode.ANY_OF -> {
                val distractors = DistractorProvider.distractorsFor(
                    question, resolvedAnswers, bank, mastery, preferredDistractors, required = TOTAL_OPTIONS - 1,
                )
                val keyed = AnswerKeyRotation.pick(correctCanonicals, timesSeen)
                val options = (listOf(PracticeOption(keyed, true)) + distractors.map { PracticeOption(it, false) })
                    .shuffled()
                PracticeQuestionState(question, resolvedAnswers, options, isMultiSelect = false, requiredSelectionCount = 1)
            }
            AnswerMode.N_OF -> {
                // Options are capped at TOTAL_OPTIONS regardless of how many answers would
                // qualify -- correct options first (never fewer than requiredCount), distractors
                // fill whatever's left.
                val correctShown = correctCanonicals.shuffled()
                    .take(minOf(correctCanonicals.size, TOTAL_OPTIONS - 1))
                    .let { if (it.size < question.requiredCount) correctCanonicals.shuffled().take(question.requiredCount) else it }
                val distractorsNeeded = (TOTAL_OPTIONS - correctShown.size).coerceAtLeast(0)
                val distractors = DistractorProvider.distractorsFor(
                    question, resolvedAnswers, bank, mastery, preferredDistractors, required = distractorsNeeded,
                )
                val options = (correctShown.map { PracticeOption(it, true) } + distractors.map { PracticeOption(it, false) })
                    .shuffled()
                PracticeQuestionState(question, resolvedAnswers, options, isMultiSelect = true, requiredSelectionCount = question.requiredCount)
            }
            AnswerMode.ALL_OF -> {
                // All correct answers must be shown (no partial credit), so they take priority;
                // distractors only fill whatever room TOTAL_OPTIONS leaves.
                val distractorsNeeded = (TOTAL_OPTIONS - correctCanonicals.size).coerceAtLeast(0)
                val distractors = DistractorProvider.distractorsFor(
                    question, resolvedAnswers, bank, mastery, preferredDistractors, required = distractorsNeeded,
                )
                val options = (correctCanonicals.map { PracticeOption(it, true) } + distractors.map { PracticeOption(it, false) })
                    .shuffled()
                PracticeQuestionState(question, resolvedAnswers, options, isMultiSelect = true, requiredSelectionCount = correctCanonicals.size)
            }
        }
    }

    private companion object {
        const val TOTAL_OPTIONS = 4
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
            feedback = PracticeFeedback(wasCorrect, alsoAccepted),
            records = state.records + PracticeAnswerRecord(current.question, wasCorrect, current.resolvedAnswers),
        )
    }

    fun next() {
        val state = _uiState.value
        if (state.feedback == null) return

        if (state.currentIndex + 1 >= state.questions.size) {
            finish(state)
        } else {
            _uiState.value = state.copy(currentIndex = state.currentIndex + 1, selectedIndices = emptySet(), feedback = null)
        }
    }

    private fun finish(state: PracticeUiState) {
        _uiState.value = state.copy(isFinished = true)

        viewModelScope.launch {
            progressRepository.recordAttempt(
                testVersion = state.testVersion,
                mode = AttemptMode.MCQ,
                startedAt = startedAtMillis,
                finishedAt = System.currentTimeMillis(),
                passed = state.passed,
                endedEarly = false,
                answers = state.records.map {
                    AnswerRecord(
                        questionId = it.question.id,
                        wasCorrect = it.wasCorrect,
                        inputMethod = InputMethod.TYPED,
                    )
                },
            )
        }
    }
}
