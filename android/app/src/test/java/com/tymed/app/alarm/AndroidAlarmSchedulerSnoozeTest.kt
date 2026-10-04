package com.tymed.app.alarm

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.tymed.app.data.DoseReminderParams
import com.tymed.app.data.dao.ScheduleWithMedication
import com.tymed.app.data.entity.Schedule
import com.tymed.app.data.entity.RecurrenceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Reproduces: ring -> Snooze -> (before the snooze follow-up fires) Taken. The snooze follow-up
 * must not still be armed afterward, or it rings again despite the dose already being marked
 * taken. */
@RunWith(RobolectricTestRunner::class)
class AndroidAlarmSchedulerSnoozeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val scheduler = AndroidAlarmScheduler(context)

    @Test
    fun `marking taken after snoozing cancels the pending snooze follow-up alarm`() {
        val scheduleId = 7L

        // Chain A rings, then AlarmActivity.onSnooze() arms the Chain B follow-up at
        // scheduleId + SNOOZE_REQUEST_CODE_OFFSET, exactly like armSnooze() does.
        armAlarm(
            context,
            AlarmSchedule(
                triggerAtMillis = System.currentTimeMillis() + 5 * 60_000L,
                requestCode = scheduleId.toInt() + SNOOZE_REQUEST_CODE_OFFSET,
                scheduleId = scheduleId.toInt(),
                medicationId = 1,
                medicationName = "Aspirin",
                dosage = "81mg",
                isPrimary = false,
                hour = 0,
                minute = 0,
                recurrenceType = "daily",
                daysOfWeek = "",
                startDate = "",
                endDate = "",
            ),
        )

        val snoozeTriggerAt = shadowOf(alarmManager).nextScheduledAlarm?.triggerAtTime
        assertTrue(
            "expected the snooze follow-up to actually be armed before Taken is pressed",
            snoozeTriggerAt != null,
        )

        val schedule = ScheduleWithMedication(
            schedule = Schedule(
                id = scheduleId,
                medicationId = 1,
                timeOfDay = "08:00",
                recurrenceType = RecurrenceType.DAILY,
            ),
            medicationName = "Aspirin",
            dosage = "81mg",
        )

        // Taken, via DoseActions -> AlarmScheduler.skipTodaysDoseReminder (the real call chain).
        scheduler.skipTodaysDoseReminder(schedule)

        // skipTodaysDoseReminder re-arms Chain A for tomorrow, so exactly one alarm should
        // remain (tomorrow's), at a materially later trigger time than the cancelled snooze.
        val remaining = shadowOf(alarmManager).scheduledAlarms
        assertEquals(
            "the snooze follow-up alarm is still armed after Taken was pressed — it will ring again",
            1,
            remaining.size,
        )
        assertTrue(
            "the one remaining alarm should be tomorrow's rearm, not the leftover snooze",
            remaining.single().triggerAtTime > snoozeTriggerAt!! + 60 * 60_000L,
        )
    }

    @Test
    fun `end-to-end via the real broadcast receiver - ring, snooze, then taken leaves only tomorrow armed`() {
        val scheduleId = 11L

        // 1. Initial arm, exactly like a schedule being created.
        scheduler.scheduleDoseReminders(
            DoseReminderParams(
                scheduleId = scheduleId,
                medicationId = 1,
                medicationName = "Aspirin",
                dosage = "81mg",
                timeOfDay = "08:00",
                recurrenceType = RecurrenceType.DAILY,
                daysOfWeek = null,
                startDate = null,
                endDate = null,
            ),
        )

        // 2. Chain A actually rings: deliver the real broadcast, exactly like AlarmManager would.
        // This is also what rearms tomorrow's occurrence under the same request code.
        val ringIntent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_REQUEST_CODE, scheduleId.toInt())
            putExtra(AlarmReceiver.EXTRA_SCHEDULE_ID, scheduleId.toInt())
            putExtra(AlarmReceiver.EXTRA_MEDICATION_ID, 1)
            putExtra(AlarmReceiver.EXTRA_MEDICATION_NAME, "Aspirin")
            putExtra(AlarmReceiver.EXTRA_DOSAGE, "81mg")
            putExtra(AlarmReceiver.EXTRA_IS_PRIMARY, true)
            putExtra(AlarmReceiver.EXTRA_HOUR, 8)
            putExtra(AlarmReceiver.EXTRA_MINUTE, 0)
            putExtra(AlarmReceiver.EXTRA_RECURRENCE_TYPE, RecurrenceType.DAILY)
            putExtra(AlarmReceiver.EXTRA_DAYS_OF_WEEK, "")
            putExtra(AlarmReceiver.EXTRA_START_DATE, "")
            putExtra(AlarmReceiver.EXTRA_END_DATE, "")
        }
        AlarmReceiver().onReceive(context, ringIntent)

        // 3. User taps Snooze: AlarmActivity.armSnooze()'s exact computation
        // (requestCode + SNOOZE_REQUEST_CODE_OFFSET, where requestCode came from the ring
        // intent above, i.e. scheduleId.toInt()).
        val snoozeRequestCode = scheduleId.toInt() + SNOOZE_REQUEST_CODE_OFFSET
        armAlarm(
            context,
            AlarmSchedule(
                triggerAtMillis = System.currentTimeMillis() + 5 * 60_000L,
                requestCode = snoozeRequestCode,
                scheduleId = scheduleId.toInt(),
                medicationId = 1,
                medicationName = "Aspirin",
                dosage = "81mg",
                isPrimary = false,
                hour = 0,
                minute = 0,
                recurrenceType = "daily",
                daysOfWeek = "",
                startDate = "",
                endDate = "",
            ),
        )

        val beforeTaken = shadowOf(alarmManager).scheduledAlarms.map { it.triggerAtTime }
        assertEquals("expected Chain A's tomorrow rearm + the snooze follow-up", 2, beforeTaken.size)

        // 4. User taps Taken (via the app, or via the alarm screen — both funnel through
        // DoseActions -> AlarmScheduler.skipTodaysDoseReminder).
        val schedule = ScheduleWithMedication(
            schedule = Schedule(
                id = scheduleId,
                medicationId = 1,
                timeOfDay = "08:00",
                recurrenceType = RecurrenceType.DAILY,
            ),
            medicationName = "Aspirin",
            dosage = "81mg",
        )
        scheduler.skipTodaysDoseReminder(schedule)

        val afterTaken = shadowOf(alarmManager).scheduledAlarms
        assertEquals(
            "the snooze follow-up is still armed after Taken was pressed — it will ring again " +
                "even though the dose is already marked taken",
            1,
            afterTaken.size,
        )
    }

    @Test
    fun `snoozing twice then tapping taken still cancels the pending second snooze`() {
        val scheduleId = 13L

        scheduler.scheduleDoseReminders(
            DoseReminderParams(
                scheduleId = scheduleId,
                medicationId = 1,
                medicationName = "Aspirin",
                dosage = "81mg",
                timeOfDay = "08:00",
                recurrenceType = RecurrenceType.DAILY,
                daysOfWeek = null,
                startDate = null,
                endDate = null,
            ),
        )

        // Ring #1 (Chain A).
        AlarmReceiver().onReceive(context, ringIntent(scheduleId, requestCode = scheduleId.toInt(), isPrimary = true))

        // Snooze #1: AlarmActivity.armSnooze(), keyed off the schedule's own id.
        val snoozeRequestCode = scheduleId.toInt() + SNOOZE_REQUEST_CODE_OFFSET
        armSnoozeLikeAlarmActivity(scheduleId, snoozeRequestCode)

        // Ring #2 (the first snooze follow-up fires; AlarmActivity now sees requestCode ==
        // snoozeRequestCode).
        AlarmReceiver().onReceive(context, ringIntent(scheduleId, requestCode = snoozeRequestCode, isPrimary = false))

        // Snooze #2: tapped from the ring #2 screen. armSnooze() still keys off the schedule's
        // own id, so this reuses (replaces) the same request code rather than compounding.
        armSnoozeLikeAlarmActivity(scheduleId, snoozeRequestCode)

        // Now the user taps Taken (from the app, or from a third ring) instead of snoozing again.
        val schedule = ScheduleWithMedication(
            schedule = Schedule(
                id = scheduleId,
                medicationId = 1,
                timeOfDay = "08:00",
                recurrenceType = RecurrenceType.DAILY,
            ),
            medicationName = "Aspirin",
            dosage = "81mg",
        )
        scheduler.skipTodaysDoseReminder(schedule)

        // Only tomorrow's Chain A rearm should remain — the second snooze follow-up must not
        // still be armed.
        val remaining = shadowOf(alarmManager).scheduledAlarms
        assertEquals(
            "the second snooze follow-up is still armed after Taken was pressed",
            1,
            remaining.size,
        )
    }

    @Test
    fun `snoozing three times in a row never compounds the request code`() {
        val scheduleId = 17L

        scheduler.scheduleDoseReminders(
            DoseReminderParams(
                scheduleId = scheduleId,
                medicationId = 1,
                medicationName = "Aspirin",
                dosage = "81mg",
                timeOfDay = "08:00",
                recurrenceType = RecurrenceType.DAILY,
                daysOfWeek = null,
                startDate = null,
                endDate = null,
            ),
        )

        val snoozeRequestCode = scheduleId.toInt() + SNOOZE_REQUEST_CODE_OFFSET

        // Ring, snooze, ring, snooze, ring, snooze — three snoozes back to back. If the request
        // code ever compounded (requestCode + OFFSET instead of scheduleId + OFFSET), each of
        // these would land at a different, ever-growing request code that cancelDoseReminders
        // could never find.
        AlarmReceiver().onReceive(context, ringIntent(scheduleId, requestCode = scheduleId.toInt(), isPrimary = true))
        armSnoozeLikeAlarmActivity(scheduleId, snoozeRequestCode)

        AlarmReceiver().onReceive(context, ringIntent(scheduleId, requestCode = snoozeRequestCode, isPrimary = false))
        armSnoozeLikeAlarmActivity(scheduleId, snoozeRequestCode)

        AlarmReceiver().onReceive(context, ringIntent(scheduleId, requestCode = snoozeRequestCode, isPrimary = false))
        armSnoozeLikeAlarmActivity(scheduleId, snoozeRequestCode)

        // Exactly one snooze-chain slot should ever be occupied, no matter how many times the
        // user re-snoozed.
        val beforeTaken = shadowOf(alarmManager).scheduledAlarms
        assertEquals("expected Chain A's tomorrow rearm + a single snooze follow-up slot", 2, beforeTaken.size)

        val schedule = ScheduleWithMedication(
            schedule = Schedule(
                id = scheduleId,
                medicationId = 1,
                timeOfDay = "08:00",
                recurrenceType = RecurrenceType.DAILY,
            ),
            medicationName = "Aspirin",
            dosage = "81mg",
        )
        scheduler.skipTodaysDoseReminder(schedule)

        val afterTaken = shadowOf(alarmManager).scheduledAlarms
        assertEquals(
            "a snooze follow-up from the 3-deep chain is still armed after Taken was pressed",
            1,
            afterTaken.size,
        )
    }

    @Test
    fun `AlarmActivity's own cancelSnoozeChain cancels the pending snooze independently of DoseActions`() {
        val scheduleId = 19L
        val snoozeRequestCode = scheduleId.toInt() + SNOOZE_REQUEST_CODE_OFFSET

        // A snooze is pending (armed exactly as AlarmActivity.armSnooze() does post-fix).
        armSnoozeLikeAlarmActivity(scheduleId, snoozeRequestCode)
        assertEquals(1, shadowOf(alarmManager).scheduledAlarms.size)

        // This mirrors AlarmActivity.onTaken()'s synchronous cancelSnoozeChain() call — a
        // belt-and-suspenders cancel fired immediately on tap, independent of (and before)
        // DoseActions' async markDose()/skipTodaysDoseReminder() path. Pre-fix this cancelled
        // requestCode + OFFSET (the *current ring's* possibly-already-offset code); post-fix it
        // cancels scheduleId + OFFSET, which is always where the live snooze actually sits.
        cancelAlarm(context, scheduleId.toInt() + SNOOZE_REQUEST_CODE_OFFSET)

        assertEquals(
            "cancelSnoozeChain's own cancel (scheduleId + OFFSET) did not reach the armed snooze",
            0,
            shadowOf(alarmManager).scheduledAlarms.size,
        )
    }

    @Test
    fun `snoozing one schedule does not disturb another schedule's independent reminder`() {
        val scheduleA = 23L
        val scheduleB = 29L

        scheduler.scheduleDoseReminders(
            DoseReminderParams(
                scheduleId = scheduleA,
                medicationId = 1,
                medicationName = "Aspirin",
                dosage = "81mg",
                timeOfDay = "08:00",
                recurrenceType = RecurrenceType.DAILY,
                daysOfWeek = null,
                startDate = null,
                endDate = null,
            ),
        )
        scheduler.scheduleDoseReminders(
            DoseReminderParams(
                scheduleId = scheduleB,
                medicationId = 2,
                medicationName = "Ibuprofen",
                dosage = "200mg",
                timeOfDay = "20:00",
                recurrenceType = RecurrenceType.DAILY,
                daysOfWeek = null,
                startDate = null,
                endDate = null,
            ),
        )
        assertEquals(2, shadowOf(alarmManager).scheduledAlarms.size)

        // Ring and snooze only schedule A, twice in a row.
        AlarmReceiver().onReceive(context, ringIntent(scheduleA, requestCode = scheduleA.toInt(), isPrimary = true))
        val snoozeRequestCodeA = scheduleA.toInt() + SNOOZE_REQUEST_CODE_OFFSET
        armSnoozeLikeAlarmActivity(scheduleA, snoozeRequestCodeA)
        AlarmReceiver().onReceive(context, ringIntent(scheduleA, requestCode = snoozeRequestCodeA, isPrimary = false))
        armSnoozeLikeAlarmActivity(scheduleA, snoozeRequestCodeA)

        // Schedule B's own reminder (armed under requestCode == scheduleB, far from A's
        // scheduleA + OFFSET slot) must still be untouched.
        val scheduleBStillArmed = shadowOf(alarmManager).scheduledAlarms.any {
            it.operation == android.app.PendingIntent.getBroadcast(
                context,
                scheduleB.toInt(),
                Intent(context, AlarmReceiver::class.java),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
            )
        }
        assertTrue("schedule B's independent reminder was disturbed by schedule A's snooze chain", scheduleBStillArmed)

        // Taken on A cancels only A's chain; B is unaffected and should still have exactly one
        // armed alarm (B's own daily reminder).
        val scheduleAObj = ScheduleWithMedication(
            schedule = Schedule(
                id = scheduleA,
                medicationId = 1,
                timeOfDay = "08:00",
                recurrenceType = RecurrenceType.DAILY,
            ),
            medicationName = "Aspirin",
            dosage = "81mg",
        )
        scheduler.skipTodaysDoseReminder(scheduleAObj)

        // A's tomorrow rearm + B's still-pending reminder = 2 total; critically, no leftover
        // snooze slot for A.
        assertEquals(2, shadowOf(alarmManager).scheduledAlarms.size)
    }

    private fun ringIntent(scheduleId: Long, requestCode: Int, isPrimary: Boolean): Intent =
        Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_REQUEST_CODE, requestCode)
            putExtra(AlarmReceiver.EXTRA_SCHEDULE_ID, scheduleId.toInt())
            putExtra(AlarmReceiver.EXTRA_MEDICATION_ID, 1)
            putExtra(AlarmReceiver.EXTRA_MEDICATION_NAME, "Aspirin")
            putExtra(AlarmReceiver.EXTRA_DOSAGE, "81mg")
            putExtra(AlarmReceiver.EXTRA_IS_PRIMARY, isPrimary)
            putExtra(AlarmReceiver.EXTRA_HOUR, 8)
            putExtra(AlarmReceiver.EXTRA_MINUTE, 0)
            putExtra(AlarmReceiver.EXTRA_RECURRENCE_TYPE, RecurrenceType.DAILY)
            putExtra(AlarmReceiver.EXTRA_DAYS_OF_WEEK, "")
            putExtra(AlarmReceiver.EXTRA_START_DATE, "")
            putExtra(AlarmReceiver.EXTRA_END_DATE, "")
        }

    /** Mirrors AlarmActivity.armSnooze(): request code = (the ring screen's own requestCode) +
     * SNOOZE_REQUEST_CODE_OFFSET. */
    private fun armSnoozeLikeAlarmActivity(scheduleId: Long, newSnoozeRequestCode: Int) {
        armAlarm(
            context,
            AlarmSchedule(
                triggerAtMillis = System.currentTimeMillis() + 5 * 60_000L,
                requestCode = newSnoozeRequestCode,
                scheduleId = scheduleId.toInt(),
                medicationId = 1,
                medicationName = "Aspirin",
                dosage = "81mg",
                isPrimary = false,
                hour = 0,
                minute = 0,
                recurrenceType = "daily",
                daysOfWeek = "",
                startDate = "",
                endDate = "",
            ),
        )
    }
}
