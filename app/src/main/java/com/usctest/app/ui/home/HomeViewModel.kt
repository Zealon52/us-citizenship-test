package com.usctest.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.usctest.app.data.ProgressRepository
import com.usctest.app.data.QuestionRepository
import com.usctest.app.data.SettingsRepository
import com.usctest.app.data.model.TestVersion
import com.usctest.app.domain.ExamFormat
import com.usctest.app.domain.TestVersionResolver
import com.usctest.app.ui.common.today
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class HomeUiState(
    val greetingName: String? = null,
    val daysUntilTest: Int? = null,
    val confidencePercent: Int = 0,
    val activeTestVersion: TestVersion = TestVersion.V2008,
    val totalQuestionCount: Int = 0,
    val practiceQuestionCount: Int = 10,
    val missedQuestionCount: Int = 0,
    val isLoading: Boolean = true,
)

class HomeViewModel(
    private val settingsRepository: SettingsRepository,
    private val progressRepository: ProgressRepository,
    private val questionRepository: QuestionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** Called from the screen every time it re-enters composition — e.g. returning from Profile —
     * since this ViewModel instance survives that round trip and won't otherwise pick up changes. */
    fun refresh() {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val profile = settingsRepository.profile.first()
            val version = TestVersionResolver.resolve(settings, profile.filingDate)

            val confidence = progressRepository.getConfidenceEstimate(version, ExamFormat.rulesFor(version))
            val totalQuestionCount = questionRepository.getQuestions(version).size
            val missedQuestionCount = progressRepository.getMissedQuestionIds(version).size

            val daysUntilTest = profile.testDate?.let { it.toEpochDays() - today().toEpochDays() }

            _uiState.value = HomeUiState(
                greetingName = profile.name,
                daysUntilTest = daysUntilTest,
                confidencePercent = confidence.confidencePercent,
                activeTestVersion = version,
                totalQuestionCount = totalQuestionCount,
                practiceQuestionCount = settings.practiceQuestionCount,
                missedQuestionCount = missedQuestionCount,
                isLoading = false,
            )
        }
    }
}
