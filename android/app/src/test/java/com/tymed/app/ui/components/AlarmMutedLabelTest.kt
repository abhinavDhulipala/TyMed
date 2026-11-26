package com.tymed.app.ui.components

import com.tymed.app.data.ReminderMute
import com.tymed.app.data.dao.DoseWithMedication
import com.tymed.app.data.entity.DoseStatus
import com.tymed.app.data.entity.IntakeLog
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class AlarmMutedLabelTest {
    private val now = ZonedDateTime.of(2026, 11, 24, 12, 0, 0, 0, ZoneId.of("America/New_York"))
    private val threeDays = ReminderMute.untilAfterDays(3, now) // Nov 27, 12:00

    private fun dose(date: String, time: String, mutedUntil: String?, status: String = DoseStatus.PENDING) = DoseWithMedication(
        log = IntakeLog(medicationId = 1, scheduleId = 1, scheduledDate = date, scheduledTime = time, status = status),
        medicationName = "Keppra",
        dosage = null,
        mutedUntil = mutedUntil,
    )

    @Test
    fun `unmuted and expired mutes show nothing`() {
        assertFalse(isAlarmMuted(dose("2026-11-24", "20:00", null), now))
        assertFalse(isAlarmMuted(dose("2026-11-24", "20:00", now.minusSeconds(1).toInstant().toString()), now))
    }

    @Test
    fun `pending doses up to the end of the mute are labelled, later ones are not`() {
        assertTrue(isAlarmMuted(dose("2026-11-24", "20:00", threeDays), now))
        assertTrue(isAlarmMuted(dose("2026-11-27", "11:59", threeDays), now))
        assertFalse(isAlarmMuted(dose("2026-11-27", "12:01", threeDays), now))
        assertFalse(isAlarmMuted(dose("2026-11-28", "08:00", threeDays), now))
    }

    @Test
    fun `a dose due exactly as the mute ends is labelled, matching what is armed`() {
        assertTrue(isAlarmMuted(dose("2026-11-27", "12:00", threeDays), now))
    }

    @Test
    fun `resolved doses are never labelled`() {
        assertFalse(isAlarmMuted(dose("2026-11-24", "20:00", threeDays, DoseStatus.TAKEN), now))
        assertFalse(isAlarmMuted(dose("2026-11-24", "20:00", threeDays, DoseStatus.SKIPPED), now))
    }

    @Test
    fun `past days are never labelled, even under an indefinite mute`() {
        assertFalse(isAlarmMuted(dose("2026-11-23", "20:00", ReminderMute.INDEFINITE), now))
    }

    @Test
    fun `an indefinite mute labels today and any future day`() {
        assertTrue(isAlarmMuted(dose("2026-11-24", "20:00", ReminderMute.INDEFINITE), now))
        assertTrue(isAlarmMuted(dose("2030-01-01", "08:00", ReminderMute.INDEFINITE), now))
    }
}
