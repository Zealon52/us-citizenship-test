package com.usctest.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.usctest.app.data.ContentUpdateResult
import com.usctest.app.data.OfficialsRepository
import com.usctest.app.data.ProgressRepository
import com.usctest.app.data.SettingsRepository
import com.usctest.app.data.model.FlashCardOrder
import com.usctest.app.data.model.StateOfficials
import com.usctest.app.data.model.TestVersion
import com.usctest.app.data.model.ThemeMode
import com.usctest.app.data.model.UserSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isLoading: Boolean = true,
    val settings: UserSettings = UserSettings(),
    val states: List<StateOfficials> = emptyList(),
    val selectedState: StateOfficials? = null,
    val officialsUpdatedAt: String = "",
    val contentUpdateMessage: String? = null,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val officialsRepository: OfficialsRepository,
    private val progressRepository: ProgressRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val officialsData = officialsRepository.getOfficialsData()
            settingsRepository.settings.collect { settings ->
                val selected = settings.selectedStateCode?.let { code ->
                    officialsData.states.firstOrNull { it.stateCode.equals(code, ignoreCase = true) }
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    settings = settings,
                    states = officialsData.states,
                    selectedState = selected,
                    officialsUpdatedAt = officialsData.updatedAt,
                )
            }
        }
    }

    fun selectState(stateCode: String) {
        viewModelScope.launch { settingsRepository.setStateAndRepresentative(stateCode, representative = null) }
    }

    fun selectRepresentative(name: String) {
        val code = _uiState.value.settings.selectedStateCode ?: return
        viewModelScope.launch { settingsRepository.setStateAndRepresentative(code, name) }
    }

    fun setFocusModeEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setFocusModeEnabled(enabled) }
    }

    fun setLargeFonts(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setLargeFonts(enabled) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setTestVersionOverride(version: TestVersion?) {
        viewModelScope.launch { settingsRepository.setTestVersionOverride(version) }
    }

    fun setPracticeConfig(questionCount: Int, passingPercent: Int) {
        viewModelScope.launch { settingsRepository.setPracticeConfig(questionCount, passingPercent) }
    }

    fun setFlashCardOrder(order: FlashCardOrder) {
        viewModelScope.launch { settingsRepository.setFlashCardOrder(order) }
    }

    fun setReminder(enabled: Boolean, hour: Int, minute: Int) {
        viewModelScope.launch { settingsRepository.setReminder(enabled, hour, minute) }
    }

    fun checkForContentUpdates() {
        viewModelScope.launch {
            val message = when (val result = officialsRepository.checkForContentUpdates()) {
                is ContentUpdateResult.Updated -> "Updated to ${result.updatedAt}"
                is ContentUpdateResult.AlreadyCurrent -> "Already up to date"
                is ContentUpdateResult.Failed -> result.reason
            }
            _uiState.value = _uiState.value.copy(contentUpdateMessage = message)
        }
    }

    fun dismissContentUpdateMessage() {
        _uiState.value = _uiState.value.copy(contentUpdateMessage = null)
    }

    fun resetProgress() {
        viewModelScope.launch { progressRepository.resetProgress() }
    }
}
