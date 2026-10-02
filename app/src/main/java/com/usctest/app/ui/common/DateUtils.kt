package com.usctest.app.ui.common

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlinx.datetime.toLocalDateTime

fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

fun LocalDate.plusDays(days: Int): LocalDate = this.plus(days, DateTimeUnit.DAY)

/** Material3 DatePicker works in UTC epoch millis. */
fun LocalDate.toEpochMillisUtc(): Long = this.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

fun Long.toLocalDateUtc(): LocalDate =
    Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date

private val monthNames = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

/** e.g. "October 14, 2025" — matches the date formatting shown in the Stitch mockups. */
fun LocalDate.toDisplayString(): String = "${monthNames[monthNumber - 1]} $dayOfMonth, $year"
