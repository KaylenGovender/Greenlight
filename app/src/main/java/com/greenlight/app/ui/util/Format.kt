package com.greenlight.app.ui.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val zone: ZoneId get() = ZoneId.systemDefault()

fun relativeDate(millis: Long, today: LocalDate = LocalDate.now(zone)): String {
    val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    return when (date) {
        today -> "today"
        today.minusDays(1) -> "yesterday"
        else -> "on " + date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
    }
}

/** "Friday 25 September" */
fun longDate(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(zone).format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()))

/** "19:00" */
fun time24(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
