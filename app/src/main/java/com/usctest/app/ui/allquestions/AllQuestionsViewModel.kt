package com.usctest.app.ui.allquestions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.usctest.app.data.OfficialsRepository
import com.usctest.app.data.ProgressRepository
import com.usctest.app.data.QuestionRepository
import com.usctest.app.data.SettingsRepository
import com.usctest.app.data.local.MasteryLevel
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

data class QuestionListItem(
    val question: Question,
    val display: AnswerDisplay,
    val masteryLevel: MasteryLevel,
    val hasAttempts: Boolean,
)

data class AllQuestionsUiState(
    val items: List<QuestionListItem> = emptyList(),
    val categories: List<String> = emptyList(),
    val selectedCategory: String? = null,
    val activeTestVersion: TestVersion = TestVersion.V2008,
    val isLoading: Boolean = true,
) {
    val visibleItems: List<QuestionListItem>
        get() = if (selectedCategory == null) items else items.filter { it.question.category == selectedCategory }
}

class AllQuestionsViewModel(
    private val settingsRepository: SettingsRepository,
    private val questionRepository: QuestionRepository,
    private val officialsRepository: OfficialsRepository,
    private val progressRepository: ProgressRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AllQuestionsUiState())
    val uiState: StateFlow<AllQuestionsUiState> = _uiState.asStateFlow()

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
                QuestionListItem(
                    question = question,
                    display = AnswerDisplayFormatter.format(question, resolvedAnswers),
                    masteryLevel = stats?.masteryLevel ?: MasteryLevel.NEW,
                    hasAttempts = (stats?.timesSeen ?: 0) > 0,
                )
            }

            _uiState.value = AllQuestionsUiState(
                items = items,
                categories = questions.map { it.category }.distinct(),
                activeTestVersion = version,
                isLoading = false,
            )
        }
    }

    fun selectCategory(category: String?) {
        _uiState.value = _uiState.value.copy(selectedCategory = category)
    }
}
