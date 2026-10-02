package com.usctest.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.usctest.app.data.model.FlashCardOrder
import com.usctest.app.data.model.TestVersion
import com.usctest.app.data.model.TestVersionSetting
import com.usctest.app.data.model.ThemeMode
import com.usctest.app.data.model.UserProfile
import com.usctest.app.data.model.UserSettings
import com.usctest.app.work.ReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

private val Context.dataStore by preferencesDataStore(name = "user_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val PROFILE_SETUP_COMPLETE = booleanPreferencesKey("profile_setup_complete")
        val PROFILE_NAME = stringPreferencesKey("profile_name")
        val PROFILE_TEST_DATE = stringPreferencesKey("profile_test_date")
        val PROFILE_FILING_DATE = stringPreferencesKey("profile_filing_date")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val HAS_SEEN_WALKTHROUGH = booleanPreferencesKey("has_seen_walkthrough")
        val SELECTED_STATE_CODE = stringPreferencesKey("selected_state_code")
        val SELECTED_REPRESENTATIVE = stringPreferencesKey("selected_representative")
        val LARGE_FONTS = booleanPreferencesKey("large_fonts")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val TEST_VERSION_MANUAL = stringPreferencesKey("test_version_manual")
        val PRACTICE_QUESTION_COUNT = intPreferencesKey("practice_question_count")
        val PRACTICE_PASSING_PERCENT = intPreferencesKey("practice_passing_percent")
        val FLASH_CARD_ORDER = stringPreferencesKey("flash_card_order")
        val FOCUS_MODE_ENABLED = booleanPreferencesKey("focus_mode_enabled")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
    }

    val settings: Flow<UserSettings> = context.dataStore.data.map { prefs -> prefs.toUserSettings() }

    val profile: Flow<UserProfile> = context.dataStore.data.map { prefs -> prefs.toUserProfile() }

    private fun Preferences.toUserProfile(): UserProfile = UserProfile(
        name = this[Keys.PROFILE_NAME],
        testDate = this[Keys.PROFILE_TEST_DATE]?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        filingDate = this[Keys.PROFILE_FILING_DATE]?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
    )

    private fun Preferences.toUserSettings(): UserSettings {
        val defaults = UserSettings()
        return UserSettings(
            profileSetupComplete = this[Keys.PROFILE_SETUP_COMPLETE] ?: defaults.profileSetupComplete,
            onboardingComplete = this[Keys.ONBOARDING_COMPLETE] ?: defaults.onboardingComplete,
            hasSeenWalkthrough = this[Keys.HAS_SEEN_WALKTHROUGH] ?: defaults.hasSeenWalkthrough,
            selectedStateCode = this[Keys.SELECTED_STATE_CODE],
            selectedRepresentative = this[Keys.SELECTED_REPRESENTATIVE],
            largeFonts = this[Keys.LARGE_FONTS] ?: defaults.largeFonts,
            themeMode = this[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: defaults.themeMode,
            testVersion = this[Keys.TEST_VERSION_MANUAL]
                ?.let { runCatching { TestVersion.valueOf(it) }.getOrNull() }
                ?.let { TestVersionSetting.Manual(it) }
                ?: TestVersionSetting.Auto,
            practiceQuestionCount = this[Keys.PRACTICE_QUESTION_COUNT] ?: defaults.practiceQuestionCount,
            practicePassingPercent = this[Keys.PRACTICE_PASSING_PERCENT] ?: defaults.practicePassingPercent,
            flashCardOrder = this[Keys.FLASH_CARD_ORDER]?.let { runCatching { FlashCardOrder.valueOf(it) }.getOrNull() }
                ?: defaults.flashCardOrder,
            focusModeEnabled = this[Keys.FOCUS_MODE_ENABLED] ?: defaults.focusModeEnabled,
            reminderEnabled = this[Keys.REMINDER_ENABLED] ?: defaults.reminderEnabled,
            reminderHour = this[Keys.REMINDER_HOUR] ?: defaults.reminderHour,
            reminderMinute = this[Keys.REMINDER_MINUTE] ?: defaults.reminderMinute,
        )
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETE] = complete }
    }

    /** Kept separate from [setOnboardingComplete] so a future "Replay intro" entry point can
     * reset just this flag without disturbing profile/state/rep data or re-triggering onboarding. */
    suspend fun setHasSeenWalkthrough(seen: Boolean) {
        context.dataStore.edit { it[Keys.HAS_SEEN_WALKTHROUGH] = seen }
    }

    /** Saves profile fields and marks profile setup as done, so Splash won't route here again. */
    suspend fun saveProfile(profile: UserProfile) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PROFILE_SETUP_COMPLETE] = true
            if (profile.name.isNullOrBlank()) prefs.remove(Keys.PROFILE_NAME) else prefs[Keys.PROFILE_NAME] = profile.name
            if (profile.testDate == null) prefs.remove(Keys.PROFILE_TEST_DATE) else prefs[Keys.PROFILE_TEST_DATE] = profile.testDate.toString()
            if (profile.filingDate == null) prefs.remove(Keys.PROFILE_FILING_DATE) else prefs[Keys.PROFILE_FILING_DATE] = profile.filingDate.toString()
        }
    }

    suspend fun setStateAndRepresentative(stateCode: String, representative: String?) {
        context.dataStore.edit {
            it[Keys.SELECTED_STATE_CODE] = stateCode
            if (representative.isNullOrBlank()) it.remove(Keys.SELECTED_REPRESENTATIVE) else it[Keys.SELECTED_REPRESENTATIVE] = representative
        }
    }

    suspend fun setLargeFonts(enabled: Boolean) {
        context.dataStore.edit { it[Keys.LARGE_FONTS] = enabled }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setTestVersionOverride(version: TestVersion?) {
        context.dataStore.edit {
            if (version == null) it.remove(Keys.TEST_VERSION_MANUAL) else it[Keys.TEST_VERSION_MANUAL] = version.name
        }
    }

    suspend fun setPracticeConfig(questionCount: Int, passingPercent: Int) {
        context.dataStore.edit {
            it[Keys.PRACTICE_QUESTION_COUNT] = questionCount
            it[Keys.PRACTICE_PASSING_PERCENT] = passingPercent
        }
    }

    suspend fun setFlashCardOrder(order: FlashCardOrder) {
        context.dataStore.edit { it[Keys.FLASH_CARD_ORDER] = order.name }
    }

    suspend fun setFocusModeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.FOCUS_MODE_ENABLED] = enabled }
    }

    suspend fun setReminder(enabled: Boolean, hour: Int, minute: Int) {
        context.dataStore.edit {
            it[Keys.REMINDER_ENABLED] = enabled
            it[Keys.REMINDER_HOUR] = hour
            it[Keys.REMINDER_MINUTE] = minute
        }
        if (enabled) {
            ReminderScheduler.schedule(context, hour, minute)
        } else {
            ReminderScheduler.cancel(context)
        }
    }
}
