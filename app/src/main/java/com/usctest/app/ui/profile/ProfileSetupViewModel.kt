package com.usctest.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.usctest.app.data.SettingsRepository
import com.usctest.app.data.model.UserProfile
import com.usctest.app.ui.common.plusDays
import com.usctest.app.ui.common.today
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

class ProfileSetupViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    /** Blank interview date silently defaults to today + 14 days, per the design doc. */
    fun submit(name: String?, testDate: LocalDate?, filingDate: LocalDate?, onDone: () -> Unit) {
        viewModelScope.launch {
            val resolvedTestDate = testDate ?: today().plusDays(14)
            settingsRepository.saveProfile(
                UserProfile(
                    name = name?.takeIf { it.isNotBlank() },
                    testDate = resolvedTestDate,
                    filingDate = filingDate,
                ),
            )
            onDone()
        }
    }
}
