package com.tymed.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** Chain B (snooze) alarms reuse the schedule id as a base, offset so they never collide with
 * the Chain A (daily) request code for the same schedule. Single named constant shared by
 * [AndroidAlarmScheduler], [AlarmReceiver], and [AlarmActivity] — previously this literal was
 * duplicated independently in TS and Kotlin. */
const val SNOOZE_REQUEST_CODE_OFFSET = 500_000

const val PREFS_NAME = "tymed_alarm_prefs"
const val PREF_FOLLOW_UP_MINUTES = "follow_up_minutes"

data class AlarmSchedule(
    val triggerAtMillis: Long,
    val requestCode: Int,
    val scheduleId: Int,
    val medicationId: Int,
    val medicationName: String,
    val dosage: String?,
    /** True for the daily dose alarm (self-reschedules +24h); false for a snooze follow-up. */
    val isPrimary: Boolean,
    /** Needed so a primary alarm can re-arm itself for the same time tomorrow. */
    val hour: Int,
    val minute: Int,
    /** Gates whether a Chain A alarm actually rings on a given day; the daily rearm poll always
     * happens regardless (until past endDate). */
    val recurrenceType: String,
    /** Comma-separated 0=Sun..6=Sat weekdays; only read when recurrenceType is "weekly". */
    val daysOfWeek: String,
    /** "YYYY-MM-DD", or "" — the monthly day-of-month anchor (also a general lower bound). */
    val startDate: String,
    /** "YYYY-MM-DD", inclusive, or "" for no end date. */
    val endDate: String,
)

/** Arms (or re-arms) a single alarm via [AlarmManager.setAlarmClock] — the one exact-alarm API
 * that needs no special permission, meant precisely for user-visible alarm-clock behavior. */
fun armAlarm(context: Context, schedule: AlarmSchedule) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    val fireIntent = Intent(context, AlarmReceiver::class.java).apply {
        putExtra(AlarmReceiver.EXTRA_REQUEST_CODE, schedule.requestCode)
        putExtra(AlarmReceiver.EXTRA_SCHEDULE_ID, schedule.scheduleId)
        putExtra(AlarmReceiver.EXTRA_MEDICATION_ID, schedule.medicationId)
        putExtra(AlarmReceiver.EXTRA_MEDICATION_NAME, schedule.medicationName)
        putExtra(AlarmReceiver.EXTRA_DOSAGE, schedule.dosage)
        putExtra(AlarmReceiver.EXTRA_IS_PRIMARY, schedule.isPrimary)
        putExtra(AlarmReceiver.EXTRA_HOUR, schedule.hour)
        putExtra(AlarmReceiver.EXTRA_MINUTE, schedule.minute)
        putExtra(AlarmReceiver.EXTRA_RECURRENCE_TYPE, schedule.recurrenceType)
        putExtra(AlarmReceiver.EXTRA_DAYS_OF_WEEK, schedule.daysOfWeek)
        putExtra(AlarmReceiver.EXTRA_START_DATE, schedule.startDate)
        putExtra(AlarmReceiver.EXTRA_END_DATE, schedule.endDate)
    }
    val operation = PendingIntent.getBroadcast(
        context,
        schedule.requestCode,
        fireIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val showIntent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent()
    val showPendingIntent = PendingIntent.getActivity(
        context,
        schedule.requestCode,
        showIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    alarmManager.setAlarmClock(
        AlarmManager.AlarmClockInfo(schedule.triggerAtMillis, showPendingIntent),
        operation,
    )
}

/** Cancels a single pending alarm by request code — used for both the daily chain and a snooze
 * follow-up (the caller decides which request code to pass). */
fun cancelAlarm(context: Context, requestCode: Int) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val fireIntent = Intent(context, AlarmReceiver::class.java)
    val operation = PendingIntent.getBroadcast(
        context,
        requestCode,
        fireIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    alarmManager.cancel(operation)
    operation.cancel()
}

fun nextOccurrenceMillis(hour: Int, minute: Int): Long {
    val cal = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, hour)
        set(java.util.Calendar.MINUTE, minute)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    if (cal.timeInMillis <= System.currentTimeMillis()) {
        cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
    }
    return cal.timeInMillis
}

fun tomorrowOccurrenceMillis(hour: Int, minute: Int): Long {
    val cal = java.util.Calendar.getInstance().apply {
        add(java.util.Calendar.DAY_OF_YEAR, 1)
        set(java.util.Calendar.HOUR_OF_DAY, hour)
        set(java.util.Calendar.MINUTE, minute)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}
