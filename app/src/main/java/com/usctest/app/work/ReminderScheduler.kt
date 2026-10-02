package com.usctest.app.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Schedules the daily study reminder as a self-rescheduling one-time [StudyReminderWorker] rather
 * than a `PeriodicWorkRequest` -- periodic work only anchors its *interval*, not a specific
 * wall-clock time, so it drifts. Each run reschedules the next one for the following day, which
 * keeps the fire time pinned to whatever the user picked in Settings.
 *
 * Survives reboot/app kill for free: WorkManager persists enqueued work in its own database and
 * re-arms the underlying alarm/job on boot without needing a `RECEIVE_BOOT_COMPLETED` receiver.
 */
object ReminderScheduler {

    private const val WORK_NAME = "daily_study_reminder"

    fun schedule(context: Context, hour: Int, minute: Int) {
        val request = OneTimeWorkRequestBuilder<StudyReminderWorker>()
            .setInitialDelay(initialDelayMillis(hour, minute), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /** Next occurrence of `hour:minute` -- today if it hasn't passed yet, tomorrow otherwise. */
    internal fun initialDelayMillis(
        hour: Int,
        minute: Int,
        zone: TimeZone = TimeZone.currentSystemDefault(),
        now: Instant = Clock.System.now(),
    ): Long {
        val nowLocal = now.toLocalDateTime(zone)
        val todayTarget = LocalDateTime(nowLocal.date, LocalTime(hour, minute))
        val target = if (todayTarget > nowLocal) todayTarget else {
            LocalDateTime(nowLocal.date.plus(1, DateTimeUnit.DAY), LocalTime(hour, minute))
        }
        return (target.toInstant(zone) - now).inWholeMilliseconds
    }
}
