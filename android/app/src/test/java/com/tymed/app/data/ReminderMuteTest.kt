package com.tymed.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderMuteTest {
    private val now = Instant.parse("2026-11-24T12:00:00Z")

    @Test
    fun `not muted, expired, and unreadable values all count as not muted`() {
        assertNull(ReminderMute.activeUntil(null, now))
        assertNull(ReminderMute.activeUntil("2026-11-24T11:59:59Z", now))
        assertNull(ReminderMute.activeUntil("2026-11-24T12:00:00Z", now))
        assertNull(ReminderMute.activeUntil("garbage", now))
    }

    @Test
    fun `a future or indefinite mute is active`() {
        assertEquals(Instant.parse("2026-11-24T12:00:01Z"), ReminderMute.activeUntil("2026-11-24T12:00:01Z", now))
        assertTrue(ReminderMute.activeUntil(ReminderMute.INDEFINITE, now) != null)
    }

    @Test
    fun `days keep the same wall-clock time across a DST change`() {
        val start = ZonedDateTime.of(2026, 10, 31, 9, 0, 0, 0, ZoneId.of("America/New_York"))

        val end = Instant.parse(ReminderMute.untilAfterDays(2, start)).atZone(start.zone)

        assertEquals("2026-11-02T09:00", end.toLocalDateTime().toString())
    }

    @Test
    fun `an empty value counts as not muted`() {
        assertNull(ReminderMute.activeUntil("", now))
    }

    @Test
    fun `only the indefinite marker is indefinite`() {
        assertTrue(ReminderMute.isIndefinite(ReminderMute.INDEFINITE))
        assertFalse(ReminderMute.isIndefinite(null))
        assertFalse(ReminderMute.isIndefinite("2026-11-24T12:00:01Z"))
        assertFalse(ReminderMute.isIndefinite(ReminderMute.untilAfterDays(999)))
    }

    @Test
    fun `the indefinite marker is itself a readable instant`() {
        // Everything that parses mutedUntil must be able to read it without a special case.
        assertEquals(Instant.parse(ReminderMute.INDEFINITE), ReminderMute.activeUntil(ReminderMute.INDEFINITE, now))
    }

    @Test
    fun `the end is the given number of days out, across month and year ends`() {
        val start = ZonedDateTime.of(2026, 12, 30, 22, 15, 0, 0, ZoneId.of("UTC"))

        assertEquals("2026-12-31T22:15:00Z", ReminderMute.untilAfterDays(1, start))
        assertEquals("2027-01-02T22:15:00Z", ReminderMute.untilAfterDays(3, start))
    }

    @Test
    fun `a mute is active until the very moment it ends`() {
        val start = now.atZone(ZoneId.of("UTC"))
        val until = ReminderMute.untilAfterDays(3, start)
        val end = start.plusDays(3).toInstant()

        assertEquals(end, ReminderMute.activeUntil(until, start.toInstant()))
        assertEquals(end, ReminderMute.activeUntil(until, end.minusMillis(1)))
        assertNull(ReminderMute.activeUntil(until, end))
    }
}
