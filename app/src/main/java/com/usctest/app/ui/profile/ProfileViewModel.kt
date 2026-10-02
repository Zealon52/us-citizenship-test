package com.usctest.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.usctest.app.data.SettingsRepository
import com.usctest.app.data.model.UserProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

class ProfileViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    /** One-shot load, not a StateFlow — avoids the caller ever observing a placeholder-empty value. */
    suspend fun loadProfile(): UserProfile = settingsRepository.profile.first()

    fun save(name: String?, testDate: LocalDate?, filingDate: LocalDate?, onDone: () -> Unit) {
        viewModelScope.launch {
            settingsRepository.saveProfile(
                UserProfile(
                    name = name?.takeIf { it.isNotBlank() },
                    testDate = testDate,
                    filingDate = filingDate,
                ),
            )
            onDone()
        }
    }
}
