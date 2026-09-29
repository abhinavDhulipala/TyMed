package com.tymed.app.data.repository

import com.tymed.app.data.FakeAlarmScheduler
import com.tymed.app.data.entity.RecurrenceType
import com.tymed.app.util.todayDateString
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScheduleSyncRepositoryTest {
    private lateinit var medications: MedicationRepository
    private lateinit var schedules: ScheduleRepository
    private lateinit var alarmScheduler: FakeAlarmScheduler
    private lateinit var sync: ScheduleSyncRepository
    private var medicationId: Long = 0

    @Before
    fun setUp() = runTest {
        val db = newInMemoryDatabase()
        medications = MedicationRepository(db.medicationDao())
        schedules = ScheduleRepository(db.scheduleDao())
        alarmScheduler = FakeAlarmScheduler()
        sync = ScheduleSyncRepository(schedules, alarmScheduler)
        medicationId = medications.createMedication(MedicationInput("Ibuprofen", "200mg", null, null, null, null))
    }

    @Test
    fun `a kept time slot keeps its schedule id`() = runTest {
        sync.syncMedicationSchedules(medicationId, "Ibuprofen", "200mg", listOf("08:00"), RecurrenceType.DAILY, null, null)
        val originalId = schedules.listSchedulesForMedication(medicationId).single().id

        // Editing to add a second time shouldn't touch the existing 08:00 schedule's id.
        sync.syncMedicationSchedules(medicationId, "Ibuprofen", "200mg", listOf("08:00", "20:00"), RecurrenceType.DAILY, null, null)
        val after = schedules.listSchedulesForMedication(medicationId)

        assertEquals(2, after.size)
        assertTrue(after.any { it.id == originalId && it.timeOfDay == "08:00" })
    }

    @Test
    fun `a removed time slot is deleted and its reminders cancelled`() = runTest {
        sync.syncMedicationSchedules(medicationId, "Ibuprofen", "200mg", listOf("08:00", "20:00"), RecurrenceType.DAILY, null, null)
        val removedId = schedules.listSchedulesForMedication(medicationId).first { it.timeOfDay == "20:00" }.id

        sync.syncMedicationSchedules(medicationId, "Ibuprofen", "200mg", listOf("08:00"), RecurrenceType.DAILY, null, null)

        assertEquals(1, schedules.listSchedulesForMedication(medicationId).size)
        assertTrue(alarmScheduler.cancelled.contains(removedId))
    }

    @Test
    fun `re-arming happens for every synced time even when only the medication name changed`() = runTest {
        sync.syncMedicationSchedules(medicationId, "Ibuprofen", "200mg", listOf("08:00"), RecurrenceType.DAILY, null, null)
        alarmScheduler.scheduled.clear()

        sync.syncMedicationSchedules(medicationId, "Ibuprofen (renamed)", "200mg", listOf("08:00"), RecurrenceType.DAILY, null, null)

        assertEquals(1, alarmScheduler.scheduled.size)
        assertEquals("Ibuprofen (renamed)", alarmScheduler.scheduled.single().medicationName)
    }

    @Test
    fun `switching to monthly anchors on today`() = runTest {
        sync.syncMedicationSchedules(medicationId, "Ibuprofen", "200mg", listOf("08:00"), RecurrenceType.MONTHLY, null, null)

        val schedule = schedules.listSchedulesForMedication(medicationId).single()
        assertEquals(todayDateString(), schedule.startDate)
    }

    @Test
    fun `editing something unrelated preserves an existing monthly anchor`() = runTest {
        sync.syncMedicationSchedules(medicationId, "Ibuprofen", "200mg", listOf("08:00"), RecurrenceType.MONTHLY, null, null)
        val anchor = schedules.listSchedulesForMedication(medicationId).single().startDate

        sync.syncMedicationSchedules(medicationId, "Ibuprofen", "200mg", listOf("09:00"), RecurrenceType.MONTHLY, null, null)

        // 08:00 -> 09:00 deletes and recreates (different time slot), but a same-time edit keeps it.
        sync.syncMedicationSchedules(medicationId, "Ibuprofen", "250mg", listOf("09:00"), RecurrenceType.MONTHLY, null, null)
        val stillAnchored = schedules.listSchedulesForMedication(medicationId).single().startDate

        assertNotEquals(null, anchor)
        assertEquals(anchor, stillAnchored)
    }
}
