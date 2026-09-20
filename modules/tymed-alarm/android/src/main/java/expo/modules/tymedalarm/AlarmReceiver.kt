package expo.modules.tymedalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

/**
 * Fires when a Chain A (daily dose) or Chain B (snooze follow-up) alarm goes off.
 * Always starts the ringing foreground service. Chain A alarms additionally re-arm
 * themselves for the same time tomorrow, so the daily schedule is self-perpetuating
 * and independent of whether the app is running or the dose is ever marked taken.
 */
class AlarmReceiver : BroadcastReceiver() {
  companion object {
    const val EXTRA_REQUEST_CODE = "requestCode"
    const val EXTRA_SCHEDULE_ID = "scheduleId"
    const val EXTRA_MEDICATION_ID = "medicationId"
    const val EXTRA_MEDICATION_NAME = "medicationName"
    const val EXTRA_DOSAGE = "dosage"
    const val EXTRA_IS_PRIMARY = "isPrimary"
    const val EXTRA_HOUR = "hour"
    const val EXTRA_MINUTE = "minute"
    const val EXTRA_DAYS_OF_WEEK = "daysOfWeek"

    /** Empty daysOfWeek means every day. Days are stored 0=Sun..6=Sat (JS convention);
     * Calendar.DAY_OF_WEEK is 1=Sun..7=Sat, hence the -1. */
    fun isActiveToday(daysOfWeek: String): Boolean {
      if (daysOfWeek.isBlank()) return true
      val todayWeekday = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1
      return daysOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }.contains(todayWeekday)
    }
  }

  override fun onReceive(context: Context, intent: Intent) {
    val requestCode = intent.getIntExtra(EXTRA_REQUEST_CODE, -1)
    val scheduleId = intent.getIntExtra(EXTRA_SCHEDULE_ID, -1)
    val medicationId = intent.getIntExtra(EXTRA_MEDICATION_ID, -1)
    val medicationName = intent.getStringExtra(EXTRA_MEDICATION_NAME) ?: "your medication"
    val dosage = intent.getStringExtra(EXTRA_DOSAGE)
    val isPrimary = intent.getBooleanExtra(EXTRA_IS_PRIMARY, false)
    val daysOfWeek = intent.getStringExtra(EXTRA_DAYS_OF_WEEK) ?: ""

    // Chain B (snooze follow-up) always rings — the user already engaged today. Chain A
    // (daily) only rings on the days this dose actually recurs on; the rearm below still
    // happens unconditionally so tomorrow (or the next active day) is unaffected.
    if (!isPrimary || isActiveToday(daysOfWeek)) {
      val serviceIntent = Intent(context, AlarmRingService::class.java).apply {
        action = AlarmRingService.ACTION_RING
        putExtra(EXTRA_REQUEST_CODE, requestCode)
        putExtra(EXTRA_SCHEDULE_ID, scheduleId)
        putExtra(EXTRA_MEDICATION_ID, medicationId)
        putExtra(EXTRA_MEDICATION_NAME, medicationName)
        putExtra(EXTRA_DOSAGE, dosage)
      }
      context.startForegroundService(serviceIntent)
    }

    if (isPrimary) {
      val hour = intent.getIntExtra(EXTRA_HOUR, 0)
      val minute = intent.getIntExtra(EXTRA_MINUTE, 0)
      rearmTomorrow(context, requestCode, scheduleId, medicationId, medicationName, dosage, hour, minute, daysOfWeek)
    }
  }

  private fun rearmTomorrow(
    context: Context,
    requestCode: Int,
    scheduleId: Int,
    medicationId: Int,
    medicationName: String,
    dosage: String?,
    hour: Int,
    minute: Int,
    daysOfWeek: String
  ) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    // Anchor to hour:minute rather than "now + 24h" so a late-firing alarm (Doze/battery
    // optimization) doesn't cause the daily time to drift.
    val next = Calendar.getInstance().apply {
      add(Calendar.DAY_OF_YEAR, 1)
      set(Calendar.HOUR_OF_DAY, hour)
      set(Calendar.MINUTE, minute)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }

    val fireIntent = Intent(context, AlarmReceiver::class.java).apply {
      putExtra(EXTRA_REQUEST_CODE, requestCode)
      putExtra(EXTRA_SCHEDULE_ID, scheduleId)
      putExtra(EXTRA_MEDICATION_ID, medicationId)
      putExtra(EXTRA_MEDICATION_NAME, medicationName)
      putExtra(EXTRA_DOSAGE, dosage)
      putExtra(EXTRA_IS_PRIMARY, true)
      putExtra(EXTRA_HOUR, hour)
      putExtra(EXTRA_MINUTE, minute)
      putExtra(EXTRA_DAYS_OF_WEEK, daysOfWeek)
    }
    val operation = PendingIntent.getBroadcast(
      context,
      requestCode,
      fireIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val showIntent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent()
    val showPendingIntent = PendingIntent.getActivity(
      context,
      requestCode,
      showIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(next.timeInMillis, showPendingIntent), operation)
  }
}
