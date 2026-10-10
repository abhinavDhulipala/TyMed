package com.tymed.app.alarm

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [RingingAlarmTracker] is a plain object (process-wide singleton), so each test clears it
 * afterward to avoid leaking state into the next test. */
class RingingAlarmTrackerTest {
    @After
    fun tearDown() {
        RingingAlarmTracker.current.value.forEach { RingingAlarmTracker.clear(it.requestCode) }
    }

    private fun info(requestCode: Int, medicationName: String = "Aspirin") = RingingAlarmInfo(
        requestCode = requestCode,
        scheduleId = requestCode,
        medicationId = 1,
        profileId = 1,
        medicationName = medicationName,
        dosage = null,
        ringingSinceMillis = 0L,
    )

    @Test
    fun `two different alarms ringing at once are both tracked`() {
        RingingAlarmTracker.start(info(1, "Aspirin"))
        RingingAlarmTracker.start(info(2, "Metformin"))

        val requestCodes = RingingAlarmTracker.current.value.map { it.requestCode }
        assertEquals(setOf(1, 2), requestCodes.toSet())
    }

    @Test
    fun `clearing one ringing alarm leaves another untouched`() {
        RingingAlarmTracker.start(info(1))
        RingingAlarmTracker.start(info(2))

        RingingAlarmTracker.clear(1)

        assertEquals(listOf(2), RingingAlarmTracker.current.value.map { it.requestCode })
    }

    @Test
    fun `starting the same request code twice replaces it rather than duplicating`() {
        RingingAlarmTracker.start(info(1, "Aspirin"))
        RingingAlarmTracker.start(info(1, "Aspirin (rearmed)"))

        val current = RingingAlarmTracker.current.value
        assertEquals(1, current.size)
        assertEquals("Aspirin (rearmed)", current.single().medicationName)
    }

    @Test
    fun `clearing an unknown request code is a harmless no-op`() {
        RingingAlarmTracker.start(info(1))

        RingingAlarmTracker.clear(999)

        assertTrue(RingingAlarmTracker.current.value.any { it.requestCode == 1 })
    }
}
