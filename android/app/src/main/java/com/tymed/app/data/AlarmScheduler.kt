package com.tymed.app.data

import com.tymed.app.data.dao.ScheduleWithMedication

data class DoseReminderParams(
    val scheduleId: Long,
    val medicationId: Long,
    val medicationName: String,
    val dosage: String?,
    val timeOfDay: String, // "HH:MM"
    val recurrenceType: String,
    val daysOfWeek: List<Int>?, // 0=Sun..6=Sat; only meaningful when recurrenceType == WEEKLY
    val startDate: String?,
    val endDate: String?,
)

/** Boundary between the data layer (which decides *when* a dose should remind) and the alarm
 * subsystem (which owns AlarmManager/notifications) — implemented by
 * [com.tymed.app.alarm.AndroidAlarmScheduler]. Kept as an interface so repositories don't need
 * to know about AlarmManager, matching the old split between src/db (schedule decisions) and
 * src/notifications/scheduler.ts + the native module (actually arming the OS alarm). */
interface AlarmScheduler {
    /** Arms (or idempotently re-arms) the daily reminder for one schedule. */
    fun scheduleDoseReminders(params: DoseReminderParams)

    /** Cancels both the daily chain and any pending snooze follow-up for one schedule. */
    fun cancelDoseReminders(scheduleId: Long)

    /** Silences an alarm that's already ringing right now (e.g. dose marked taken from the UI
     * while it was sounding) — cancelling the pending entry alone has no effect on one in
     * progress. */
    fun stopRinging()

    /** Stops today's already-armed alarm from ringing without breaking the recurring chain: the
     * daily alarm re-arms itself on every fire, so a bare cancel would silently kill future days
     * too. Re-arms for tomorrow's occurrence instead (a no-op past the schedule's end date). */
    fun skipTodaysDoseReminder(schedule: ScheduleWithMedication)

    /** Re-arms every given (already-enabled) schedule's next occurrence — a backstop called once
     * on app start in case a reboot wasn't caught for any reason (BootReceiver missed it, or a
     * fresh debug install never received BOOT_COMPLETED at all). */
    fun rearmAllScheduleAlarms(schedules: List<ScheduleWithMedication>)

    /** Mirrors the follow-up (snooze) interval into fast local storage the alarm can read
     * synchronously at ring time, without an async Room query on the activity's hot path. The
     * Room `app_settings` row (via SettingsRepository) stays the source of truth for display. */
    fun setFollowUpMinutes(minutes: Int)
}
