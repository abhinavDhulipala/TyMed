package com.tymed.app.data.repository

import com.tymed.app.data.FakeAlarmScheduler
import com.tymed.app.data.ReminderMute
import com.tymed.app.data.TymedDatabase
import com.tymed.app.data.entity.DoseStatus
import com.tymed.app.data.entity.IntakeLog
import com.tymed.app.data.entity.Profile
import com.tymed.app.data.entity.RecurrenceType
import com.tymed.app.data.entity.Schedule
import com.tymed.app.util.todayDateString
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class MuteActionsTest {
    private lateinit var db: TymedDatabase
    private lateinit var medications: MedicationRepository
    private lateinit var schedules: ScheduleRepository
    private lateinit var logs: IntakeLogRepository
    private lateinit var alarmScheduler: FakeAlarmScheduler
    private lateinit var muteActions: MuteActions
    private var medicationId: Long = 0
    private var scheduleId: Long = 0
    private var otherMedicationId: Long = 0

    @Before
    fun setUp() = runTest {
        db = newInMemoryDatabase()
        medications = MedicationRepository(db.medicationDao())
        schedules = ScheduleRepository(db.scheduleDao())
        logs = IntakeLogRepository(db.intakeLogDao(), db.scheduleDao())
        alarmScheduler = FakeAlarmScheduler()
        muteActions = MuteActions(medications, schedules, logs, alarmScheduler)

        val daily = RecurrenceInput(RecurrenceType.DAILY, null, null, null)
        medicationId = medications.createMedication(TEST_PROFILE_ID, MedicationInput("Keppra", "250mg", null, null, 10, null))
        scheduleId = schedules.createSchedule(medicationId, "08:00", daily)
        otherMedicationId = medications.createMedication(TEST_PROFILE_ID, MedicationInput("Aspirin", null, null, null, null, null))
        schedules.createSchedule(otherMedicationId, "08:00", daily)
    }

    @Test
    fun `muting for days stores the end and re-arms only that medication with it`() = runTest {
        val before = Instant.now()

        muteActions.mute(medicationId, 3)

        val stored = medications.getMedication(medicationId)!!.mutedUntil!!
        val drift = Duration.between(before, Instant.parse(stored)).minusDays(3).abs()
        assertTrue("expected about 3 days, off by $drift", drift < Duration.ofHours(2))
        val rearmed = alarmScheduler.rearmed.single().single()
        assertEquals(scheduleId, rearmed.schedule.id)
        assertEquals(stored, rearmed.mutedUntil)
        assertNull(medications.getMedication(otherMedicationId)?.mutedUntil)
    }

    @Test
    fun `muting without days is indefinite`() = runTest {
        muteActions.mute(medicationId, null)

        assertEquals(ReminderMute.INDEFINITE, medications.getMedication(medicationId)?.mutedUntil)
        assertEquals(ReminderMute.INDEFINITE, alarmScheduler.rearmed.single().single().mutedUntil)
    }

    @Test
    fun `unmuting clears the mute and re-arms`() = runTest {
        muteActions.mute(medicationId, null)
        alarmScheduler.rearmed.clear()

        muteActions.unmute(medicationId)

        assertNull(medications.getMedication(medicationId)?.mutedUntil)
        assertNull(alarmScheduler.rearmed.single().single().mutedUntil)
    }

    @Test
    fun `unmuting does not bring back todays alarm for a dose already taken`() = runTest {
        muteActions.mute(medicationId, null)
        val log = logs.findOrCreateTodayLogForSchedule(scheduleId)
        logs.setLogStatus(log.id, DoseStatus.TAKEN)
        alarmScheduler.rearmed.clear()

        muteActions.unmute(medicationId)

        assertTrue(alarmScheduler.rearmed.flatten().isEmpty())
        assertEquals(scheduleId, alarmScheduler.skipped.single().schedule.id)
        assertNull(alarmScheduler.skipped.single().mutedUntil)
    }

    @Test
    fun `disabled schedules are never armed by muting or unmuting`() = runTest {
        val disabledId = db.scheduleDao().insert(Schedule(medicationId = medicationId, timeOfDay = "20:00", enabled = 0))

        muteActions.mute(medicationId, 2)
        muteActions.unmute(medicationId)

        assertTrue(alarmScheduler.rearmed.flatten().none { it.schedule.id == disabledId })
    }

    @Test
    fun `a muted medications doses are still listed, flagged with the mute`() = runTest {
        muteActions.mute(medicationId, null)

        val dose = logs.getDosesForDate(TEST_PROFILE_ID, todayDateString()).single { it.log.medicationId == medicationId }

        assertEquals(DoseStatus.PENDING, dose.log.status)
        assertEquals(ReminderMute.INDEFINITE, dose.mutedUntil)
    }

    @Test
    fun `a non-positive number of days is rejected without touching anything`() = runTest {
        for (days in listOf(0, -1)) {
            val error = runCatching { muteActions.mute(medicationId, days) }.exceptionOrNull()
            assertTrue("days=$days", error is IllegalArgumentException)
        }

        assertNull(medications.getMedication(medicationId)?.mutedUntil)
        assertTrue(alarmScheduler.rearmed.isEmpty())
    }

    @Test
    fun `muting again replaces the previous mute, whichever way it changes`() = runTest {
        muteActions.mute(medicationId, null)
        muteActions.mute(medicationId, 5)
        val fiveDays = medications.getMedication(medicationId)!!.mutedUntil!!
        muteActions.mute(medicationId, 1)
        val oneDay = medications.getMedication(medicationId)!!.mutedUntil!!
        muteActions.mute(medicationId, null)

        assertTrue(Instant.parse(oneDay) < Instant.parse(fiveDays))
        assertEquals(ReminderMute.INDEFINITE, medications.getMedication(medicationId)?.mutedUntil)
        assertEquals(
            listOf(ReminderMute.INDEFINITE, fiveDays, oneDay, ReminderMute.INDEFINITE),
            alarmScheduler.rearmed.map { it.single().mutedUntil },
        )
    }

    @Test
    fun `every time of day is muted together`() = runTest {
        val eveningId = schedules.createSchedule(medicationId, "20:00", RecurrenceInput(RecurrenceType.DAILY, null, null, null))

        muteActions.mute(medicationId, null)

        assertEquals(setOf(scheduleId, eveningId), alarmScheduler.rearmed.single().map { it.schedule.id }.toSet())
        assertTrue(alarmScheduler.rearmed.single().all { it.mutedUntil == ReminderMute.INDEFINITE })
    }

    @Test
    fun `unmuting re-arms only the doses not yet resolved today`() = runTest {
        val daily = RecurrenceInput(RecurrenceType.DAILY, null, null, null)
        val skippedId = schedules.createSchedule(medicationId, "14:00", daily)
        val pendingId = schedules.createSchedule(medicationId, "20:00", daily)
        val noLogId = schedules.createSchedule(medicationId, "22:00", daily)
        muteActions.mute(medicationId, null)
        logs.setLogStatus(logs.findOrCreateTodayLogForSchedule(scheduleId).id, DoseStatus.TAKEN)
        logs.setLogStatus(logs.findOrCreateTodayLogForSchedule(skippedId).id, DoseStatus.SKIPPED)
        logs.findOrCreateTodayLogForSchedule(pendingId)
        alarmScheduler.rearmed.clear()

        muteActions.unmute(medicationId)

        assertEquals(setOf(pendingId, noLogId), alarmScheduler.rearmed.flatten().map { it.schedule.id }.toSet())
        assertEquals(setOf(scheduleId, skippedId), alarmScheduler.skipped.map { it.schedule.id }.toSet())
    }

    @Test
    fun `a dose taken on another day does not hold back todays alarm`() = runTest {
        val yesterday = todayDateString(LocalDate.now().minusDays(1))
        db.intakeLogDao().insert(
            IntakeLog(medicationId = medicationId, scheduleId = scheduleId, scheduledDate = yesterday, scheduledTime = "08:00", status = DoseStatus.TAKEN),
        )
        muteActions.mute(medicationId, null)
        alarmScheduler.rearmed.clear()

        muteActions.unmute(medicationId)

        assertEquals(scheduleId, alarmScheduler.rearmed.flatten().single().schedule.id)
        assertTrue(alarmScheduler.skipped.isEmpty())
    }

    @Test
    fun `the same medication under another profile is unaffected`() = runTest {
        val otherProfile = db.profileDao().insert(Profile(name = "Other", colorHex = "#3D405B", createdAt = "2026-01-01T00:00:00Z"))
        val twin = medications.createMedication(otherProfile, MedicationInput("Keppra", "250mg", null, null, null, null))

        muteActions.mute(medicationId, null)

        assertNull(medications.getMedication(twin)?.mutedUntil)
    }

    @Test
    fun `muting a medication that no longer exists does nothing`() = runTest {
        muteActions.mute(9_999, 3)
        muteActions.unmute(9_999)

        assertTrue(alarmScheduler.rearmed.flatten().isEmpty())
        assertTrue(alarmScheduler.skipped.isEmpty())
    }

    @Test
    fun `the list re-armed on boot and app start carries each medications own mute`() = runTest {
        muteActions.mute(medicationId, null)

        val mutes = schedules.listAllEnabledSchedulesWithMedication().associate { it.schedule.medicationId to it.mutedUntil }

        assertEquals(mapOf(medicationId to ReminderMute.INDEFINITE, otherMedicationId to null), mutes)
    }
}
