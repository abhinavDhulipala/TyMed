package com.tymed.app.alarm

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tymed.app.data.entity.DoseStatus
import com.tymed.app.data.entity.RecurrenceType
import com.tymed.app.data.repository.DoseActions
import com.tymed.app.data.repository.IntakeLogRepository
import com.tymed.app.data.repository.MedicationInput
import com.tymed.app.data.repository.MedicationRepository
import com.tymed.app.data.repository.MuteActions
import com.tymed.app.data.repository.ScheduleRepository
import com.tymed.app.data.repository.ScheduleSyncRepository
import com.tymed.app.data.repository.TEST_PROFILE_ID
import com.tymed.app.data.repository.newInMemoryDatabase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Instant

/** Muting through the real database, repositories and [AndroidAlarmScheduler] at once, asserting
 * on what AlarmManager actually holds. The unit tests either side of [com.tymed.app.data.AlarmScheduler]
 * can't notice a mute that gets stored but lost on its way to the alarm. */
@RunWith(RobolectricTestRunner::class)
class MuteEndToEndTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val scheduler = AndroidAlarmScheduler(context)
    private lateinit var medications: MedicationRepository
    private lateinit var schedules: ScheduleRepository
    private lateinit var logs: IntakeLogRepository
    private lateinit var sync: ScheduleSyncRepository
    private lateinit var doseActions: DoseActions
    private lateinit var muteActions: MuteActions
    private var keppra: Long = 0
    private var aspirin: Long = 0

    @Before
    fun setUp() = runTest {
        val db = newInMemoryDatabase()
        medications = MedicationRepository(db.medicationDao())
        schedules = ScheduleRepository(db.scheduleDao())
        logs = IntakeLogRepository(db.intakeLogDao(), db.scheduleDao())
        sync = ScheduleSyncRepository(medications, schedules, scheduler)
        doseActions = DoseActions(logs, medications, schedules, scheduler)
        muteActions = MuteActions(medications, schedules, logs, scheduler)
        keppra = medications.createMedication(TEST_PROFILE_ID, MedicationInput("Keppra", "250mg", null, null, 10, null))
        aspirin = medications.createMedication(TEST_PROFILE_ID, MedicationInput("Aspirin", null, null, null, null, null))
        setTimes(keppra, "08:00", "20:00")
        setTimes(aspirin, "09:00")
    }

    private suspend fun setTimes(id: Long, vararg times: String) =
        sync.syncMedicationSchedules(id, TEST_PROFILE_ID, "Med $id", null, times.toList(), RecurrenceType.DAILY, null, null)

    /** Trigger times of every alarm armed for [medicationId]. */
    private fun armed(medicationId: Long): List<Instant> = shadowOf(alarmManager).scheduledAlarms
        .filter { shadowOf(it.operation).savedIntent.getIntExtra(AlarmReceiver.EXTRA_MEDICATION_ID, -1) == medicationId.toInt() }
        .map { Instant.ofEpochMilli(it.triggerAtTime) }

    private suspend fun muteEnd() = Instant.parse(medications.getMedication(keppra)!!.mutedUntil)

    private suspend fun rearmLikeAppStart() = scheduler.rearmAllScheduleAlarms(schedules.listAllEnabledSchedulesWithMedication())

    private suspend fun assertOnlyAspirinArmed() {
        assertEquals(emptyList<Instant>(), armed(keppra))
        assertEquals(1, armed(aspirin).size)
    }

    private suspend fun assertKeppraArmedOnlyAfterMute(count: Int) {
        val end = muteEnd()
        assertEquals(count, armed(keppra).size)
        assertTrue("${armed(keppra)} must all be after $end", armed(keppra).all { it > end })
    }

    @Test
    fun `an indefinite mute silences every time of that medication and nothing else`() = runTest {
        assertEquals(2, armed(keppra).size)

        muteActions.mute(keppra, null)

        assertOnlyAspirinArmed()
    }

    @Test
    fun `a timed mute moves every time of that medication past its end`() = runTest {
        muteActions.mute(keppra, 3)

        assertKeppraArmedOnlyAfterMute(2)
        assertEquals(1, armed(aspirin).size)
    }

    @Test
    fun `restarting the app does not undo a mute`() = runTest {
        muteActions.mute(keppra, null)
        rearmLikeAppStart()
        assertOnlyAspirinArmed()

        muteActions.mute(keppra, 3)
        rearmLikeAppStart()
        assertKeppraArmedOnlyAfterMute(2)
    }

    @Test
    fun `editing the medication or adding a time does not undo a mute`() = runTest {
        muteActions.mute(keppra, null)
        medications.updateMedication(keppra, MedicationInput("Keppra", "500mg", null, null, 10, null))
        setTimes(keppra, "08:00", "14:00", "20:00")
        assertOnlyAspirinArmed()

        muteActions.mute(keppra, 3)
        setTimes(keppra, "08:00", "14:00", "21:00")
        assertKeppraArmedOnlyAfterMute(3)
    }

    @Test
    fun `marking, undoing and skipping doses does not undo a mute`() = runTest {
        for (days in listOf(null, 3)) {
            muteActions.mute(keppra, days)
            val logIds = schedules.listSchedulesForMedication(keppra).map { logs.findOrCreateTodayLogForSchedule(it.id).id }
            for (status in listOf(DoseStatus.TAKEN, DoseStatus.PENDING, DoseStatus.SKIPPED)) {
                logIds.forEach { doseActions.markDose(it, status) }
                if (days == null) assertOnlyAspirinArmed() else assertKeppraArmedOnlyAfterMute(2)
            }
        }
    }

    @Test
    fun `switching between indefinite and timed mutes follows the latest`() = runTest {
        muteActions.mute(keppra, null)
        muteActions.mute(keppra, 3)
        assertKeppraArmedOnlyAfterMute(2)

        muteActions.mute(keppra, null)
        assertOnlyAspirinArmed()
    }

    @Test
    fun `unmuting restores exactly the alarms there were before`() = runTest {
        val before = armed(keppra).toSet()

        for (days in listOf(null, 3)) {
            muteActions.mute(keppra, days)
            muteActions.unmute(keppra)
            assertEquals("days=$days", before, armed(keppra).toSet())
        }
    }

    @Test
    fun `unmuting after todays doses were taken arms nothing before tomorrow`() = runTest {
        muteActions.mute(keppra, null)
        schedules.listSchedulesForMedication(keppra).forEach {
            doseActions.markDose(logs.findOrCreateTodayLogForSchedule(it.id).id, DoseStatus.TAKEN)
        }

        muteActions.unmute(keppra)

        val tomorrow = setOf(tomorrowOccurrenceMillis(8, 0), tomorrowOccurrenceMillis(20, 0)).map(Instant::ofEpochMilli).toSet()
        assertEquals(tomorrow, armed(keppra).toSet())
    }
}
