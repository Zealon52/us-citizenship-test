package com.usctest.app.ui.walkthrough

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.usctest.app.data.SettingsRepository
import com.usctest.app.ui.common.today
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class WalkthroughUiState(
    val isLoading: Boolean = true,
    val name: String? = null,
    val daysUntilTest: Int? = null,
)

/**
 * Runs once, after onboarding and before Home -- see the app plan's first-launch walkthrough
 * spec. Screen 4's closing copy is personalized from the profile data onboarding already
 * collected, which is the whole reason this runs *after* onboarding rather than before it.
 */
class WalkthroughViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(WalkthroughUiState())
    val uiState: StateFlow<WalkthroughUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = settingsRepository.profile.first()
            val daysUntilTest = profile.testDate?.let { it.toEpochDays() - today().toEpochDays() }
            _uiState.value = WalkthroughUiState(isLoading = false, name = profile.name, daysUntilTest = daysUntilTest)
        }
    }

    /** Skip marks this "seen" too -- the flag means "don't show this again," not "the user read
     * every word," so skipping shouldn't bring the walkthrough back on the next cold start. */
    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            settingsRepository.setHasSeenWalkthrough(true)
            onDone()
        }
    }
}
