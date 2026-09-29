package com.tymed.app.data

import com.tymed.app.data.entity.RecurrenceType
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Single source of truth for "does this schedule recur on this date" — used by both the doses-
 * for-date UI logic and [com.tymed.app.alarm.AlarmReceiver]. Unlike the old TS+Kotlin split
 * (src/utils/schedule.ts and AlarmReceiver.kt::isActiveOn, kept in sync by hand), there's only
 * one process now, so only one implementation. */
data class RecurrenceRule(
    val recurrenceType: String,
    /** 0=Sun..6=Sat. Only read when recurrenceType == WEEKLY. */
    val daysOfWeek: List<Int>?,
    /** "YYYY-MM-DD". Day-of-month anchor for MONTHLY; lower bound for all types. */
    val startDate: String?,
    /** "YYYY-MM-DD", inclusive upper bound. */
    val endDate: String?,
) {
    companion object {
        private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

        fun parseDate(dateStr: String): LocalDate = LocalDate.parse(dateStr, ISO)

        /** True when a schedule recurs on the given date, per its recurrence type, day-of-week
         * selection (weekly only), and optional start/end date bounds. */
        fun isActiveOn(rule: RecurrenceRule, dateStr: String): Boolean {
            if (rule.startDate != null && dateStr < rule.startDate) return false
            if (rule.endDate != null && dateStr > rule.endDate) return false

            return when (rule.recurrenceType) {
                RecurrenceType.DAILY -> true
                RecurrenceType.WEEKLY -> {
                    val weekday = isoDowToJsWeekday(parseDate(dateStr).dayOfWeek.value)
                    rule.daysOfWeek != null && rule.daysOfWeek.contains(weekday)
                }
                RecurrenceType.MONTHLY -> {
                    val anchorStr = rule.startDate ?: return false
                    val anchor = parseDate(anchorStr)
                    val date = parseDate(dateStr)
                    // A schedule anchored on e.g. the 31st fires on the last day of shorter
                    // months instead of never firing that month.
                    val daysInMonth = date.lengthOfMonth()
                    val targetDay = minOf(anchor.dayOfMonth, daysInMonth)
                    date.dayOfMonth == targetDay
                }
                // Unrecognized value fails open rather than silently going quiet.
                else -> true
            }
        }

        /** Structural equality for two recurrence rules — used to decide whether an existing
         * schedule needs updating when a medication is edited. */
        fun equals(a: RecurrenceRule, b: RecurrenceRule): Boolean {
            if (a.recurrenceType != b.recurrenceType) return false
            if (a.startDate != b.startDate) return false
            if (a.endDate != b.endDate) return false
            if (a.daysOfWeek == null || b.daysOfWeek == null) return a.daysOfWeek == b.daysOfWeek
            return a.daysOfWeek == b.daysOfWeek
        }

        /** java.time's DayOfWeek is 1=Mon..7=Sun; the app's on-disk convention (matching the old
         * JS Date#getDay()) is 0=Sun..6=Sat. */
        private fun isoDowToJsWeekday(isoDayOfWeek: Int): Int = isoDayOfWeek % 7
    }
}
