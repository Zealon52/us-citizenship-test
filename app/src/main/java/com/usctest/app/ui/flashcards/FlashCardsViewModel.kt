package com.usctest.app.ui.flashcards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.usctest.app.data.OfficialsRepository
import com.usctest.app.data.ProgressRepository
import com.usctest.app.data.QuestionRepository
import com.usctest.app.data.SettingsRepository
import com.usctest.app.data.local.MasteryLevel
import com.usctest.app.data.model.FlashCardOrder
import com.usctest.app.data.model.Question
import com.usctest.app.data.model.TestVersion
import com.usctest.app.domain.AnswerDisplay
import com.usctest.app.domain.AnswerDisplayFormatter
import com.usctest.app.domain.StateAnswerResolver
import com.usctest.app.domain.TestVersionResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class FlashCardItem(
    val question: Question,
    val display: AnswerDisplay,
    val masteryLevel: MasteryLevel,
    val hasAttempts: Boolean,
    val markedKnown: Boolean = false,
)

data class FlashCardsUiState(
    val items: List<FlashCardItem> = emptyList(),
    val testVersion: TestVersion = TestVersion.V2008,
    val isLoading: Boolean = true,
)

class FlashCardsViewModel(
    private val settingsRepository: SettingsRepository,
    private val questionRepository: QuestionRepository,
    private val officialsRepository: OfficialsRepository,
    private val progressRepository: ProgressRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FlashCardsUiState())
    val uiState: StateFlow<FlashCardsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val profile = settingsRepository.profile.first()
            val version = TestVersionResolver.resolve(settings, profile.filingDate)

            val questions = questionRepository.getQuestions(version)
            val officials = settings.selectedStateCode?.let { officialsRepository.getStateOfficials(it) }
            val statsById = progressRepository.getAllStats(version).associateBy { it.questionId }

            val items = questions.map { question ->
                val resolvedAnswers = StateAnswerResolver
                    .resolveAcceptableAnswers(question, officials, settings.selectedRepresentative)
                    .map { it.canonical }
                val stats = statsById[question.id]
                FlashCardItem(
                    question = question,
                    display = AnswerDisplayFormatter.format(question, resolvedAnswers),
                    masteryLevel = stats?.masteryLevel ?: MasteryLevel.NEW,
                    hasAttempts = (stats?.timesSeen ?: 0) > 0,
                )
            }

            val ordered = when (settings.flashCardOrder) {
                FlashCardOrder.SEQUENTIAL -> items
                FlashCardOrder.RANDOM -> items.shuffled()
                FlashCardOrder.WEAKEST_FIRST -> items.sortedBy { it.masteryLevel.ordinal }
            }

            _uiState.value = FlashCardsUiState(items = ordered, testVersion = version, isLoading = false)
        }
    }

    fun markKnown(index: Int) {
        val state = _uiState.value
        val item = state.items.getOrNull(index) ?: return
        if (item.markedKnown) return

        _uiState.value = state.copy(
            items = state.items.mapIndexed { i, it -> if (i == index) it.copy(markedKnown = true) else it },
        )

        viewModelScope.launch {
            progressRepository.markKnown(item.question.id, state.testVersion)
        }
    }
}
