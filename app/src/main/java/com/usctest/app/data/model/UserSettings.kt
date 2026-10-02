package com.usctest.app.data.model

enum class ThemeMode { AUTO, LIGHT, DARK }

enum class FlashCardOrder { SEQUENTIAL, RANDOM, WEAKEST_FIRST }

data class UserSettings(
    val profileSetupComplete: Boolean = false,
    val onboardingComplete: Boolean = false,
    val hasSeenWalkthrough: Boolean = false,
    val selectedStateCode: String? = null,
    val selectedRepresentative: String? = null,
    val largeFonts: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val testVersion: TestVersionSetting = TestVersionSetting.Auto,
    val practiceQuestionCount: Int = 10,
    val practicePassingPercent: Int = 60,
    val flashCardOrder: FlashCardOrder = FlashCardOrder.SEQUENTIAL,
    val focusModeEnabled: Boolean = false,
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 19,
    val reminderMinute: Int = 0,
)

/** Test version is normally auto-selected from filing date; user can override. */
sealed interface TestVersionSetting {
    data object Auto : TestVersionSetting
    data class Manual(val version: TestVersion) : TestVersionSetting
}
