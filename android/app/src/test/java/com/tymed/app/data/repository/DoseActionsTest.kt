package com.tymed.app.data.repository

import com.tymed.app.data.FakeAlarmScheduler
import com.tymed.app.data.entity.DoseStatus
import com.tymed.app.data.entity.RecurrenceType
import com.tymed.app.util.todayDateString
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DoseActionsTest {
    private lateinit var medications: MedicationRepository
    private lateinit var schedules: ScheduleRepository
    private lateinit var logs: IntakeLogRepository
    private lateinit var alarmScheduler: FakeAlarmScheduler
    private lateinit var doseActions: DoseActions
    private var medicationId: Long = 0
    private var scheduleId: Long = 0

    @Before
    fun setUp() = runTest {
        val db = newInMemoryDatabase()
        medications = MedicationRepository(db.medicationDao())
        schedules = ScheduleRepository(db.scheduleDao())
        logs = IntakeLogRepository(db.intakeLogDao(), db.scheduleDao())
        alarmScheduler = FakeAlarmScheduler()
        doseActions = DoseActions(logs, medications, schedules, alarmScheduler)

        medicationId = medications.createMedication(TEST_PROFILE_ID, MedicationInput("Aspirin", "81mg", null, null, 10, null))
        scheduleId = schedules.createSchedule(medicationId, "08:00", RecurrenceInput(RecurrenceType.DAILY, null, null, null))
    }

    private suspend fun todaysLogId(): Long =
        logs.getDosesForDate(TEST_PROFILE_ID, todayDateString()).first { it.log.scheduleId == scheduleId }.log.id

    @Test
    fun `marking a dose taken decrements pill count and marking it pending again restores it`() = runTest {
        val logId = todaysLogId()

        doseActions.markDose(logId, DoseStatus.TAKEN)
        assertEquals(9, medications.getMedication(medicationId)?.pillsRemaining)

        doseActions.markDose(logId, DoseStatus.PENDING)
        assertEquals(10, medications.getMedication(medicationId)?.pillsRemaining)
    }

    @Test
    fun `marking a dose taken sets takenAt, undoing clears it`() = runTest {
        val logId = todaysLogId()

        doseActions.markDose(logId, DoseStatus.TAKEN)
        assertEquals(DoseStatus.TAKEN, logs.getLog(logId)?.status)
        assertEquals(true, logs.getLog(logId)?.takenAt != null)

        doseActions.markDose(logId, DoseStatus.PENDING)
        assertNull(logs.getLog(logId)?.takenAt)
    }

    @Test
    fun `resolving todays dose ahead of its alarm tells the scheduler to skip today`() = runTest {
        val logId = todaysLogId()

        doseActions.markDose(logId, DoseStatus.TAKEN)

        // stopRinging()/cancel are AndroidAlarmScheduler's own internal follow-through (covered
        // by its own tests, if any device-level ones are added) — DoseActions' contract with the
        // scheduler is just this one call.
        assertEquals(1, alarmScheduler.skipped.size)
        assertEquals(scheduleId, alarmScheduler.skipped.single().schedule.id)
    }

    @Test
    fun `skipping a dose does not touch pill count`() = runTest {
        val logId = todaysLogId()

        doseActions.markDose(logId, DoseStatus.SKIPPED)

        assertEquals(10, medications.getMedication(medicationId)?.pillsRemaining)
        assertEquals(1, alarmScheduler.skipped.size)
    }
}
