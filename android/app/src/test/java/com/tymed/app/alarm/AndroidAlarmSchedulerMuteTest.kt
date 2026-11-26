package com.tymed.app.alarm

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tymed.app.data.DoseReminderParams
import com.tymed.app.data.ReminderMute
import com.tymed.app.data.dao.ScheduleWithMedication
import com.tymed.app.data.entity.RecurrenceType
import com.tymed.app.data.entity.Schedule
import com.tymed.app.util.todayDateString
import java.time.Duration
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
class AndroidAlarmSchedulerMuteTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val scheduler = AndroidAlarmScheduler(context)
    private val scheduleId = 7L
    private val armedTimes get() = shadowOf(alarmManager).scheduledAlarms.map { it.triggerAtTime }

    private fun params(mutedUntil: String?, endDate: String? = null) = DoseReminderParams(
        scheduleId = scheduleId,
        medicationId = 1,
        profileId = 1,
        medicationName = "Keppra",
        dosage = "250mg",
        timeOfDay = "08:00",
        recurrenceType = RecurrenceType.DAILY,
        daysOfWeek = null,
        startDate = null,
        endDate = endDate,
        mutedUntil = mutedUntil,
    )

    private fun schedule(mutedUntil: String?) = ScheduleWithMedication(
        schedule = Schedule(id = scheduleId, medicationId = 1, timeOfDay = "08:00"),
        profileId = 1,
        medicationName = "Keppra",
        dosage = "250mg",
        mutedUntil = mutedUntil,
    )

    /** 08:00 on the first day it falls after [end] — computed independently of the scheduler. */
    private fun firstEightAfter(end: Instant): Long {
        val local = end.atZone(ZoneId.systemDefault())
        val date = if (local.toLocalTime() < LocalTime.of(8, 0)) local.toLocalDate() else local.toLocalDate().plusDays(1)
        return date.atTime(8, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    @Test
    fun `a timed mute replaces the armed alarm with the first dose after it ends`() {
        scheduler.scheduleDoseReminders(params(mutedUntil = null))
        val mutedUntil = ReminderMute.untilAfterDays(3)

        scheduler.scheduleDoseReminders(params(mutedUntil))

        assertEquals(listOf(firstEightAfter(Instant.parse(mutedUntil))), armedTimes)
    }

    @Test
    fun `a mute silences exactly as many daily doses as days, whatever time it is set`() {
        for (hour in listOf(1, 7, 9, 23)) {
            val start = ZonedDateTime.now().plusDays(1).withHour(hour).withMinute(30)
            val unmutedNext = nextOccurrenceMillis(8, 0, afterMillis = start.toInstant().toEpochMilli())
            val mutedNext = nextOccurrenceMillis(8, 0, afterMillis = Instant.parse(ReminderMute.untilAfterDays(3, start)).toEpochMilli())

            val zone = ZoneId.systemDefault()
            val skippedDays = LocalDate.ofInstant(Instant.ofEpochMilli(mutedNext), zone).toEpochDay() -
                LocalDate.ofInstant(Instant.ofEpochMilli(unmutedNext), zone).toEpochDay()
            assertEquals("set at $hour:30", 3L, skippedDays)
        }
    }

    @Test
    fun `an indefinite mute leaves nothing armed, including a pending snooze`() {
        scheduler.scheduleDoseReminders(params(mutedUntil = null))
        armSnooze()
        assertEquals(2, armedTimes.size)

        scheduler.scheduleDoseReminders(params(ReminderMute.INDEFINITE))

        assertTrue(armedTimes.isEmpty())
    }

    @Test
    fun `unmuting arms the next dose again`() {
        scheduler.scheduleDoseReminders(params(ReminderMute.INDEFINITE))

        scheduler.scheduleDoseReminders(params(mutedUntil = null))

        assertEquals(listOf(firstEightAfter(Instant.now())), armedTimes)
    }

    @Test
    fun `an expired mute has no effect`() {
        scheduler.scheduleDoseReminders(params(Instant.now().minusSeconds(60).toString()))

        assertEquals(listOf(firstEightAfter(Instant.now())), armedTimes)
    }

    @Test
    fun `a mute outlasting the schedules end date leaves nothing armed`() {
        scheduler.scheduleDoseReminders(params(ReminderMute.untilAfterDays(10), endDate = todayDateString(LocalDate.now().plusDays(5))))

        assertTrue(armedTimes.isEmpty())
    }

    @Test
    fun `the app-start and reboot re-arm keeps a mute in force`() {
        val mutedUntil = ReminderMute.untilAfterDays(3)

        scheduler.rearmAllScheduleAlarms(listOf(schedule(mutedUntil)))
        assertEquals(listOf(firstEightAfter(Instant.parse(mutedUntil))), armedTimes)

        scheduler.rearmAllScheduleAlarms(listOf(schedule(ReminderMute.INDEFINITE)))
        assertTrue(armedTimes.isEmpty())
    }

    @Test
    fun `marking a muted dose taken does not arm tomorrows alarm`() {
        val mutedUntil = ReminderMute.untilAfterDays(3)

        scheduler.skipTodaysDoseReminder(schedule(mutedUntil))
        assertEquals(listOf(firstEightAfter(Instant.parse(mutedUntil))), armedTimes)

        scheduler.skipTodaysDoseReminder(schedule(ReminderMute.INDEFINITE))
        assertTrue(armedTimes.isEmpty())
    }

    @Test
    fun `the next occurrence is strictly after the mute ends`() {
        val eight = ZonedDateTime.now().plusDays(3).withHour(8).withMinute(0).withSecond(0).withNano(0)
        val end = eight.toInstant().toEpochMilli()

        assertEquals(end, nextOccurrenceMillis(8, 0, afterMillis = end - 1))
        // Dose due exactly as the mute ends: still silenced.
        assertEquals(eight.plusDays(1).toInstant().toEpochMilli(), nextOccurrenceMillis(8, 0, afterMillis = end))
    }

    @Test
    fun `the first dose after a mute keeps its wall-clock time across a DST change`() = withTimeZone("America/New_York") {
        val zone = ZoneId.of("America/New_York")
        // Clocks go forward 2027-03-14 02:00.
        val end = ZonedDateTime.of(2027, 3, 13, 9, 0, 0, 0, zone).toInstant().toEpochMilli()

        val next = Instant.ofEpochMilli(nextOccurrenceMillis(8, 0, afterMillis = end)).atZone(zone)

        assertEquals("2027-03-14T08:00", next.toLocalDateTime().toString())
    }

    @Test
    fun `re-arming after travelling uses local time, still never before the mute ends`() {
        val mutedUntil = ReminderMute.untilAfterDays(3)
        for (zone in listOf("America/New_York", "Asia/Tokyo", "Pacific/Auckland", "Pacific/Honolulu")) {
            withTimeZone(zone) {
                scheduler.rearmAllScheduleAlarms(listOf(schedule(mutedUntil)))

                val armed = Instant.ofEpochMilli(armedTimes.single())
                assertTrue(zone, armed > Instant.parse(mutedUntil))
                assertTrue(zone, armed <= Instant.parse(mutedUntil).plus(Duration.ofHours(25)))
                assertEquals(zone, LocalTime.of(8, 0), armed.atZone(ZoneId.of(zone)).toLocalTime())
            }
        }
    }

    @Test
    fun `muting one schedule leaves another schedules alarm alone`() {
        scheduler.scheduleDoseReminders(params(mutedUntil = null).copy(scheduleId = 8L, medicationId = 2))
        scheduler.scheduleDoseReminders(params(mutedUntil = null))
        assertEquals(2, armedTimes.size)

        scheduler.scheduleDoseReminders(params(ReminderMute.INDEFINITE))

        assertEquals(8, armedIntents.single().getIntExtra(AlarmReceiver.EXTRA_SCHEDULE_ID, -1))
    }

    @Test
    fun `a timed mute drops a pending snooze too`() {
        armSnooze()

        scheduler.scheduleDoseReminders(params(ReminderMute.untilAfterDays(3)))

        assertEquals(scheduleId.toInt(), armedIntents.single().getIntExtra(AlarmReceiver.EXTRA_REQUEST_CODE, -1))
    }

    @Test
    fun `re-arming an unmuted schedule never drops a pending snooze`() {
        armSnooze()

        for (mutedUntil in listOf(null, Instant.now().minusSeconds(60).toString())) {
            scheduler.scheduleDoseReminders(params(mutedUntil))
            assertEquals("mutedUntil=$mutedUntil", 2, armedTimes.size)
        }
    }

    @Test
    fun `an unreadable mute rings rather than staying silent`() {
        scheduler.scheduleDoseReminders(params("not a timestamp"))
        assertEquals(listOf(firstEightAfter(Instant.now())), armedTimes)

        scheduler.skipTodaysDoseReminder(schedule("not a timestamp"))
        assertEquals(1, armedTimes.size)
    }

    @Test
    fun `an end date on or after the first dose back still arms it`() {
        val mutedUntil = ReminderMute.untilAfterDays(3)
        val first = firstEightAfter(Instant.parse(mutedUntil))
        val firstDate = Instant.ofEpochMilli(first).atZone(ZoneId.systemDefault()).toLocalDate()

        scheduler.scheduleDoseReminders(params(mutedUntil, endDate = todayDateString(firstDate)))
        assertEquals(listOf(first), armedTimes)

        scheduler.scheduleDoseReminders(params(mutedUntil, endDate = todayDateString(firstDate.minusDays(1))))
        assertTrue(armedTimes.isEmpty())
    }

    @Test
    fun `marking a muted dose taken respects the end date`() {
        val mutedUntil = ReminderMute.untilAfterDays(3)
        val base = schedule(mutedUntil)

        scheduler.skipTodaysDoseReminder(base.copy(schedule = base.schedule.copy(endDate = todayDateString(LocalDate.now().plusDays(1)))))

        assertTrue(armedTimes.isEmpty())
    }

    @Test
    fun `marking a dose taken after the mute expired arms tomorrow as usual`() {
        scheduler.skipTodaysDoseReminder(schedule(Instant.now().minusSeconds(60).toString()))

        assertEquals(listOf(tomorrowOccurrenceMillis(8, 0)), armedTimes)
    }

    @Test
    fun `marking a dose taken just before the mute ends never re-arms today`() {
        // A dose late enough that its first occurrence after the mute is still today's.
        val base = schedule(Instant.now().plusSeconds(60).toString())

        scheduler.skipTodaysDoseReminder(base.copy(schedule = base.schedule.copy(timeOfDay = "23:59")))

        assertEquals(listOf(tomorrowOccurrenceMillis(23, 59)), armedTimes)
    }

    @Test
    fun `the alarm armed past a mute carries the recurrence it needs to keep going`() {
        scheduler.scheduleDoseReminders(
            params(ReminderMute.untilAfterDays(3), endDate = "2099-12-31").copy(
                recurrenceType = RecurrenceType.WEEKLY,
                daysOfWeek = listOf(1, 3, 5),
                startDate = "2026-01-01",
            ),
        )

        val intent = armedIntents.single()
        assertEquals(RecurrenceType.WEEKLY, intent.getStringExtra(AlarmReceiver.EXTRA_RECURRENCE_TYPE))
        assertEquals("1,3,5", intent.getStringExtra(AlarmReceiver.EXTRA_DAYS_OF_WEEK))
        assertEquals("2026-01-01", intent.getStringExtra(AlarmReceiver.EXTRA_START_DATE))
        assertEquals("2099-12-31", intent.getStringExtra(AlarmReceiver.EXTRA_END_DATE))
        assertEquals(8, intent.getIntExtra(AlarmReceiver.EXTRA_HOUR, -1))
        assertEquals(0, intent.getIntExtra(AlarmReceiver.EXTRA_MINUTE, -1))
        assertTrue(intent.getBooleanExtra(AlarmReceiver.EXTRA_IS_PRIMARY, false))
    }

    @Test
    fun `once the mute is over its alarm rings and the daily chain resumes`() {
        scheduler.scheduleDoseReminders(params(ReminderMute.untilAfterDays(3)))
        val intent = armedIntents.single()

        try {
            // Delivered exactly as AlarmManager would; the receiver re-arms from the real clock.
            AlarmReceiver().onReceive(context, intent)

            assertEquals(scheduleId.toInt(), RingingAlarmTracker.current.value.single().scheduleId)
            assertEquals(listOf(tomorrowOccurrenceMillis(8, 0)), armedTimes)
        } finally {
            RingingAlarmTracker.clear(scheduleId.toInt())
        }
    }

    private val armedIntents
        get() = shadowOf(alarmManager).scheduledAlarms.map { shadowOf(it.operation).savedIntent }

    private fun armSnooze() = armAlarm(
        context,
        AlarmSchedule(
            triggerAtMillis = System.currentTimeMillis() + 5 * 60_000L,
            requestCode = scheduleId.toInt() + SNOOZE_REQUEST_CODE_OFFSET,
            scheduleId = scheduleId.toInt(),
            medicationId = 1,
            profileId = 1,
            medicationName = "Keppra",
            dosage = "250mg",
            isPrimary = false,
            hour = 0,
            minute = 0,
            recurrenceType = RecurrenceType.DAILY,
            daysOfWeek = "",
            startDate = "",
            endDate = "",
        ),
    )

    private fun withTimeZone(id: String, block: () -> Unit) {
        val original = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(id))
        try {
            block()
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
