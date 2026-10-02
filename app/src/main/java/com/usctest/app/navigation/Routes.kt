package com.usctest.app.navigation

object Routes {
    const val SPLASH = "splash"
    const val PROFILE_SETUP = "profile_setup"
    const val ONBOARDING = "onboarding"
    const val WALKTHROUGH = "walkthrough"
    const val HOME = "home"
    const val PROFILE = "profile"
    const val PROGRESS = "progress"
    const val SETTINGS = "settings"
    const val ALL_QUESTIONS = "all_questions"
    const val RECALL_MODE = "recall_mode"
    const val PRACTICE_TEST = "practice_test"
    const val FLASH_CARDS = "flash_cards"
    const val REVIEW_MISSED = "review_missed"
    const val COMING_SOON = "coming_soon/{title}"

    fun comingSoon(title: String) = "coming_soon/$title"
}
