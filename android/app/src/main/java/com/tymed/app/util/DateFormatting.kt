package com.tymed.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

fun todayDateString(date: LocalDate = LocalDate.now()): String = date.format(ISO)

fun parseDateStr(dateStr: String): LocalDate = LocalDate.parse(dateStr, ISO)

/** Set once at app startup (and whenever the user changes the setting) from SettingsRepository —
 * formatTime is called from render paths all over the UI, so it reads a shared flag rather than
 * every caller threading the setting through as a parameter. */
object TimeFormatPreference {
    @Volatile
    var use24Hour: Boolean = false
}

fun formatTime(hhmm: String): String {
    val parts = hhmm.split(":")
    val h = parts[0].toInt()
    val m = parts[1].toInt()
    if (TimeFormatPreference.use24Hour) {
        return "%02d:%02d".format(h, m)
    }
    val period = if (h >= 12) "PM" else "AM"
    val hour12 = if (h % 12 == 0) 12 else h % 12
    return "%d:%02d %s".format(hour12, m, period)
}

/** Formats an [IntakeLog.takenAt]-style ISO instant string as a local wall-clock time, honoring
 * [TimeFormatPreference] the same way [formatTime] does for scheduled times. Falls back to the
 * raw string if it isn't parseable, rather than crashing a list row over a malformed timestamp. */
fun formatInstantTime(isoInstant: String): String {
    val zoned = try {
        Instant.parse(isoInstant).atZone(ZoneId.systemDefault())
    } catch (error: Exception) {
        return isoInstant
    }
    return formatTime("%02d:%02d".format(zoned.hour, zoned.minute))
}

/** The local calendar date an ISO instant string falls on, e.g. for grouping/labeling
 * [Incident]-style timestamps the same way [IntakeLog] rows are labeled by [formatDateLabel].
 * Falls back to today's date if the string isn't parseable. */
fun formatInstantDate(isoInstant: String): String =
    try {
        formatDateLabel(todayDateString(Instant.parse(isoInstant).atZone(ZoneId.systemDefault()).toLocalDate()))
    } catch (error: Exception) {
        formatDateLabel(todayDateString())
    }

/** "1h 05m", "5m 30s", or "45s" — whichever units apply, dropping leading zero units. */
fun formatDurationSeconds(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "%dh %02dm".format(hours, minutes)
        minutes > 0 -> "%dm %02ds".format(minutes, seconds)
        else -> "%ds".format(seconds)
    }
}

fun formatDateLabel(dateStr: String): String {
    val today = todayDateString()
    val yesterday = todayDateString(LocalDate.now().minusDays(1))
    return when (dateStr) {
        today -> "Today"
        yesterday -> "Yesterday"
        else -> dateStr
    }
}

/** Full readable date, e.g. "Monday, September 15" — falls back to "Today"/"Yesterday". */
fun formatFullDateLabel(dateStr: String): String {
    val label = formatDateLabel(dateStr)
    if (label == "Today" || label == "Yesterday") return label
    val date = parseDateStr(dateStr)
    val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US)
    val month = date.month.getDisplayName(TextStyle.FULL, Locale.US)
    return "$weekday, $month ${date.dayOfMonth}"
}
