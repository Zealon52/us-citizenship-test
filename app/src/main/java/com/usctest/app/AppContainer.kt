package com.usctest.app

import android.content.Context
import com.usctest.app.data.OfficialsRepository
import com.usctest.app.data.ProgressRepository
import com.usctest.app.data.QuestionRepository
import com.usctest.app.data.SettingsRepository
import com.usctest.app.data.StudyPlanRepository
import com.usctest.app.data.local.AppDatabase
import com.usctest.app.speech.SpeechRecognizerManager

/**
 * Simple manual DI container. The app is small enough that a framework like Hilt would be
 * unneeded ceremony; revisit if the dependency graph grows significantly.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = AppDatabase.getInstance(appContext)

    val settingsRepository = SettingsRepository(appContext)
    val questionRepository = QuestionRepository(appContext)
    val officialsRepository = OfficialsRepository(appContext)
    val progressRepository = ProgressRepository(
        questionStatsDao = database.questionStatsDao(),
        attemptDao = database.attemptDao(),
        attemptAnswerDao = database.attemptAnswerDao(),
        questionRepository = questionRepository,
    )
    val studyPlanRepository = StudyPlanRepository(
        studyPlanDao = database.studyPlanDao(),
        studySessionDao = database.studySessionDao(),
    )

    // Held here (not a local val) so it isn't garbage collected mid-download and loses its
    // ModelDownloadListener callback before the download finishes.
    private val startupSpeechRecognizerManager = SpeechRecognizerManager(appContext)

    init {
        // Kick this off at app startup rather than when Recall Mode opens, so the on-device
        // speech model (if missing) has as much time as possible to finish downloading before
        // someone actually taps the mic.
        startupSpeechRecognizerManager.ensureOfflineModelDownloaded()
    }
}
