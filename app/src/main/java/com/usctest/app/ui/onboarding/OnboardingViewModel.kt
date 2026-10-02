package com.usctest.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.usctest.app.data.OfficialsRepository
import com.usctest.app.data.SettingsRepository
import com.usctest.app.data.model.StateOfficials
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val states: List<StateOfficials> = emptyList(),
    val isLoading: Boolean = true,
)

class OnboardingViewModel(
    private val officialsRepository: OfficialsRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val data = officialsRepository.getOfficialsData()
            _uiState.value = OnboardingUiState(states = data.states, isLoading = false)
        }
    }

    fun completeOnboarding(stateCode: String, representative: String?, onDone: () -> Unit) {
        viewModelScope.launch {
            settingsRepository.setStateAndRepresentative(stateCode, representative)
            settingsRepository.setOnboardingComplete(true)
            onDone()
        }
    }
}
