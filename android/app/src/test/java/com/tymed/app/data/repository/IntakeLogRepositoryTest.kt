package com.tymed.app.data.repository

import com.tymed.app.data.TymedDatabase
import com.tymed.app.data.entity.DoseStatus
import com.tymed.app.data.entity.RecurrenceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IntakeLogRepositoryTest {
    private lateinit var db: TymedDatabase
    private lateinit var medications: MedicationRepository
    private lateinit var schedules: ScheduleRepository
    private lateinit var logs: IntakeLogRepository
    private var medicationId: Long = 0

    @Before
    fun setUp() = runTest {
        db = newInMemoryDatabase()
        medications = MedicationRepository(db.medicationDao())
        schedules = ScheduleRepository(db.scheduleDao())
        logs = IntakeLogRepository(db.intakeLogDao(), db.scheduleDao())
        medicationId = medications.createMedication(TEST_PROFILE_ID, MedicationInput("Aspirin", "81mg", null, null, null, null))
    }

    @Test
    fun `ensureLogsForDate creates a pending row only once per schedule per date`() = runTest {
        schedules.createSchedule(medicationId, "08:00", RecurrenceInput(RecurrenceType.DAILY, null, null, null))

        val date = "2026-09-20"
        val first = logs.getDosesForDate(TEST_PROFILE_ID, date)
        val second = logs.getDosesForDate(TEST_PROFILE_ID, date)

        assertEquals(1, first.size)
        assertEquals(1, second.size)
        assertEquals(first[0].log.id, second[0].log.id)
        assertEquals(DoseStatus.PENDING, first[0].log.status)
    }

    @Test
    fun `ensureLogsForDate skips a schedule that is not active on that date`() = runTest {
        schedules.createSchedule(
            medicationId,
            "08:00",
            RecurrenceInput(RecurrenceType.WEEKLY, daysOfWeek = listOf(1, 3, 5), startDate = null, endDate = null),
        )

        // 2026-09-20 is a Sunday, not in [Mon, Wed, Fri].
        val doses = logs.getDosesForDate(TEST_PROFILE_ID, "2026-09-20")

        assertTrue(doses.isEmpty())
    }

    @Test
    fun `findOrCreateTodayLogForSchedule creates a row even off-schedule as a safety net`() = runTest {
        val scheduleId = schedules.createSchedule(
            medicationId,
            "08:00",
            RecurrenceInput(RecurrenceType.WEEKLY, daysOfWeek = listOf(1), startDate = null, endDate = null),
        )

        // Even if today isn't Monday, the alarm having fired is itself proof the dose is due.
        val log = logs.findOrCreateTodayLogForSchedule(scheduleId)

        assertEquals(DoseStatus.PENDING, log.status)
        assertEquals(medicationId, log.medicationId)
    }

    @Test
    fun `concurrent ensureLogsForDate calls never create duplicate rows`() = runTest {
        schedules.createSchedule(medicationId, "08:00", RecurrenceInput(RecurrenceType.DAILY, null, null, null))
        val date = "2026-09-20"

        // Reproduces a real race: e.g. a ViewModel's init{} and a LifecycleResumeEffect's first
        // firing both call refresh() moments apart, each doing ensureLogsForDate's check-then-
        // insert concurrently. Without the unique index + IGNORE, this used to create duplicate
        // "pending" rows for the same schedule+date, showing as a duplicated dose on screen.
        List(20) { async(Dispatchers.Default) { logs.ensureLogsForDate(TEST_PROFILE_ID, date) } }.awaitAll()

        assertEquals(1, logs.getDosesForDate(TEST_PROFILE_ID, date).size)
    }

    @Test
    fun `getDailyAdherence sums taken and resolved per day`() = runTest {
        schedules.createSchedule(medicationId, "08:00", RecurrenceInput(RecurrenceType.DAILY, null, null, null))
        val date = "2026-09-20"
        val log = logs.getDosesForDate(TEST_PROFILE_ID, date).first()
        logs.setLogStatus(log.log.id, DoseStatus.TAKEN)

        val adherence = logs.getDailyAdherence(TEST_PROFILE_ID, date, date)

        assertEquals(1, adherence[date]?.taken)
        assertEquals(1, adherence[date]?.resolved)
    }
}
