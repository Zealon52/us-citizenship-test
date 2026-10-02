package com.usctest.app.work

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * A small pool of reminder copy, picked by the current epoch day so consecutive days cycle
 * through different messages instead of repeating the same line every night.
 */
object ReminderMessages {

    private data class Message(val title: String, val body: String)

    private val pool = listOf(
        Message("Time for a quick civics review", "A few minutes of practice keeps your answers sharp for the interview."),
        Message("Keep your streak going", "Today's practice session is waiting for you."),
        Message("A little goes a long way", "Review a few questions today — recall gets easier with repetition."),
        Message("Ready for today's practice?", "Your civics questions are ready whenever you are."),
        Message("Don't lose momentum", "Even a short session today keeps what you've learned fresh."),
        Message("Practice makes the interview easier", "Take a few minutes today to review with Recall Mode or Practice Test."),
    )

    fun today(zone: TimeZone = TimeZone.currentSystemDefault()): Pair<String, String> {
        val epochDay = Clock.System.todayIn(zone).toEpochDays()
        val message = pool[(epochDay % pool.size).toInt().let { if (it < 0) it + pool.size else it }]
        return message.title to message.body
    }
}
