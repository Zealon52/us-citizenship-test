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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
            coroutineScope {
                val settingsDeferred = async { settingsRepository.settings.first() }
                val profileDeferred = async { settingsRepository.profile.first() }
                val settings = settingsDeferred.await()
                val profile = profileDeferred.await()
                val version = TestVersionResolver.resolve(settings, profile.filingDate)
                val rules = ExamFormat.rulesFor(version)

                // Independent reads (two Room queries + a Monte Carlo simulation) run concurrently
                // instead of sequentially -- this is what caused the countdown/ring to visibly lag
                // behind the rest of Home, which renders immediately from static defaults.
                val confidenceDeferred = async { progressRepository.getConfidenceEstimate(version, rules) }
                val totalQuestionCountDeferred = async { questionRepository.getQuestions(version).size }
                val missedQuestionCountDeferred = async { progressRepository.getMissedQuestionIds(version).size }

                val daysUntilTest = profile.testDate?.let { it.toEpochDays() - today().toEpochDays() }

                _uiState.value = HomeUiState(
                    greetingName = profile.name,
                    daysUntilTest = daysUntilTest,
                    confidencePercent = confidenceDeferred.await().confidencePercent,
                    activeTestVersion = version,
                    totalQuestionCount = totalQuestionCountDeferred.await(),
                    practiceQuestionCount = settings.practiceQuestionCount,
                    missedQuestionCount = missedQuestionCountDeferred.await(),
                    isLoading = false,
                )
            }
        }
    }
}
