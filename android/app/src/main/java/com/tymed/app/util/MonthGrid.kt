package com.tymed.app.util

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

data class CalendarDay(
    val date: LocalDate,
    val dateStr: String,
    val day: Int,
    val inCurrentMonth: Boolean,
    val isToday: Boolean,
    val isFuture: Boolean,
)

val WEEKDAY_LABELS = listOf("S", "M", "T", "W", "T", "F", "S")

fun monthLabel(year: Int, month: Int): String {
    val ym = YearMonth.of(year, month)
    return "${ym.month.getDisplayName(TextStyle.FULL, Locale.US)} $year"
}

/** Full-weeks calendar grid (padded with adjacent-month days), Sunday-first. */
fun buildMonthGrid(year: Int, month: Int): List<CalendarDay> {
    val today = LocalDate.now()
    val firstOfMonth = LocalDate.of(year, month, 1)
    // java.time DayOfWeek is 1=Mon..7=Sun; convert to 0=Sun..6=Sat to find the grid's leading pad.
    val firstWeekday = firstOfMonth.dayOfWeek.value % 7
    val gridStart = firstOfMonth.minusDays(firstWeekday.toLong())

    val daysInMonth = YearMonth.of(year, month).lengthOfMonth()
    val totalCells = ((firstWeekday + daysInMonth + 6) / 7) * 7

    return (0 until totalCells).map { offset ->
        val date = gridStart.plusDays(offset.toLong())
        CalendarDay(
            date = date,
            dateStr = todayDateString(date),
            day = date.dayOfMonth,
            inCurrentMonth = date.month == firstOfMonth.month && date.year == firstOfMonth.year,
            isToday = date == today,
            isFuture = date.isAfter(today),
        )
    }
}
