package com.tymed.app.util

import com.tymed.app.data.ReminderMute
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class FormatMutedUntilTest {
    private val originalUse24Hour = TimeFormatPreference.use24Hour

    @After
    fun tearDown() {
        TimeFormatPreference.use24Hour = originalUse24Hour
    }

    private fun at(date: LocalDate, hour: Int, minute: Int) =
        date.atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toString()

    private fun label(date: LocalDate) = date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US))

    @Test
    fun `an indefinite mute never shows a date`() {
        assertEquals("until you unmute", formatMutedUntil(ReminderMute.INDEFINITE))
    }

    @Test
    fun `the last day reads as part of the sentence`() {
        TimeFormatPreference.use24Hour = false
        assertEquals("until today, 1:10 PM", formatMutedUntil(at(LocalDate.now(), 13, 10)))
    }

    @Test
    fun `another year is spelled out`() {
        TimeFormatPreference.use24Hour = false
        val date = LocalDate.now().plusYears(1)
        assertEquals("until ${label(date)}, ${date.year}, 9:05 AM", formatMutedUntil(at(date, 9, 5)))
    }

    @Test
    fun `this year is not`() {
        TimeFormatPreference.use24Hour = false
        // Any day this year other than today/yesterday, which have their own labels.
        val today = LocalDate.now()
        val date = listOf(today.withDayOfYear(1), today.withDayOfYear(100)).first { it != today && it != today.minusDays(1) }
        assertEquals("until ${label(date)}, 9:05 AM", formatMutedUntil(at(date, 9, 5)))
    }

    @Test
    fun `the 24-hour setting is honoured`() {
        TimeFormatPreference.use24Hour = true
        val date = LocalDate.now().plusYears(1)
        assertEquals("until ${label(date)}, ${date.year}, 13:10", formatMutedUntil(at(date, 13, 10)))
    }
}
