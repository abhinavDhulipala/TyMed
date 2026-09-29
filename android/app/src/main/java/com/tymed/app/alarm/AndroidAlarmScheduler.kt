package com.tymed.app.alarm

import android.content.Context
import android.content.Intent
import com.tymed.app.data.AlarmScheduler
import com.tymed.app.data.DoseReminderParams
import com.tymed.app.data.dao.ScheduleWithMedication
import com.tymed.app.data.repository.decodeDaysOfWeek
import com.tymed.app.util.todayDateString
import java.time.LocalDate

/** Encodes for the alarm receiver: comma-separated weekday numbers, or "" for none/unused. */
private fun encodeDaysOfWeekCsv(daysOfWeek: List<Int>?): String = daysOfWeek?.takeIf { it.isNotEmpty() }?.joinToString(",") ?: ""

/** Real (non-test) implementation of [AlarmScheduler] backed by [android.app.AlarmManager] and
 * the alarm ringing subsystem in this package. A schedule already past its end date is never
 * armed at all. */
class AndroidAlarmScheduler(private val context: Context) : AlarmScheduler {

    override fun scheduleDoseReminders(params: DoseReminderParams) {
        val (hour, minute) = parseTimeOfDay(params.timeOfDay)
        if (params.endDate != null && params.endDate < todayDateString()) {
            cancelDoseReminders(params.scheduleId)
            return
        }
        armAlarm(
            context,
            AlarmSchedule(
                triggerAtMillis = nextOccurrenceMillis(hour, minute),
                requestCode = params.scheduleId.toInt(),
                scheduleId = params.scheduleId.toInt(),
                medicationId = params.medicationId.toInt(),
                medicationName = params.medicationName,
                dosage = params.dosage,
                isPrimary = true,
                hour = hour,
                minute = minute,
                recurrenceType = params.recurrenceType,
                daysOfWeek = encodeDaysOfWeekCsv(params.daysOfWeek),
                startDate = params.startDate ?: "",
                endDate = params.endDate ?: "",
            ),
        )
    }

    override fun cancelDoseReminders(scheduleId: Long) {
        cancelAlarm(context, scheduleId.toInt())
        cancelAlarm(context, scheduleId.toInt() + SNOOZE_REQUEST_CODE_OFFSET)
    }

    override fun stopRinging() {
        context.startService(
            Intent(context, AlarmRingService::class.java).apply { action = AlarmRingService.ACTION_STOP },
        )
    }

    /** Stops today's already-armed alarm from ringing without breaking the recurring chain: the
     * daily alarm re-arms itself on every fire, so a bare cancel would silently kill future days
     * too. Re-arms for tomorrow's occurrence instead (skipped if that would already be past the
     * schedule's end date — mirrors AlarmReceiver's own rearm guard). */
    override fun skipTodaysDoseReminder(schedule: ScheduleWithMedication) {
        val id = schedule.schedule.id
        cancelDoseReminders(id)
        // Cancelling above only stops *future* fires — if this schedule's alarm is ringing right
        // now (e.g. the dose was marked taken from the app UI while it sounded), it keeps
        // ringing until told to stop explicitly.
        stopRinging()

        val endDate = schedule.schedule.endDate
        val tomorrow = todayDateString(LocalDate.now().plusDays(1))
        if (endDate != null && tomorrow > endDate) return

        val (hour, minute) = parseTimeOfDay(schedule.schedule.timeOfDay)
        armAlarm(
            context,
            AlarmSchedule(
                triggerAtMillis = tomorrowOccurrenceMillis(hour, minute),
                requestCode = id.toInt(),
                scheduleId = id.toInt(),
                medicationId = schedule.schedule.medicationId.toInt(),
                medicationName = schedule.medicationName,
                dosage = schedule.dosage,
                isPrimary = true,
                hour = hour,
                minute = minute,
                recurrenceType = schedule.schedule.recurrenceType,
                daysOfWeek = encodeDaysOfWeekCsv(decodeDaysOfWeek(schedule.schedule.daysOfWeek)),
                startDate = schedule.schedule.startDate ?: "",
                endDate = endDate ?: "",
            ),
        )
    }

    /** Re-arms every given (already-enabled) schedule's next occurrence — a backstop for
     * [BootReceiver], called once on app start in case a reboot wasn't caught for any reason
     * (e.g. a fresh debug install that never received BOOT_COMPLETED). */
    override fun rearmAllScheduleAlarms(schedules: List<ScheduleWithMedication>) {
        for (schedule in schedules) {
            scheduleDoseReminders(
                DoseReminderParams(
                    scheduleId = schedule.schedule.id,
                    medicationId = schedule.schedule.medicationId,
                    medicationName = schedule.medicationName,
                    dosage = schedule.dosage,
                    timeOfDay = schedule.schedule.timeOfDay,
                    recurrenceType = schedule.schedule.recurrenceType,
                    daysOfWeek = decodeDaysOfWeek(schedule.schedule.daysOfWeek),
                    startDate = schedule.schedule.startDate,
                    endDate = schedule.schedule.endDate,
                ),
            )
        }
    }

    override fun setFollowUpMinutes(minutes: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(PREF_FOLLOW_UP_MINUTES, minutes)
            .apply()
    }

    private fun parseTimeOfDay(timeOfDay: String): Pair<Int, Int> {
        val (h, m) = timeOfDay.split(":")
        return h.toInt() to m.toInt()
    }
}
