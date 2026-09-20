package expo.modules.tymedalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar
import java.util.Locale

/**
 * Fires when a Chain A (daily dose) or Chain B (snooze follow-up) alarm goes off.
 * Always starts the ringing foreground service. Chain A alarms additionally re-arm
 * themselves for the same time the next day, so the daily poll is self-perpetuating
 * and independent of whether the app is running or the dose is ever marked taken —
 * whether it actually *rings* that day is gated by [isActiveOn]. This mirrors
 * src/utils/schedule.ts's isScheduleActiveOn; keep the two in sync.
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
    const val EXTRA_RECURRENCE_TYPE = "recurrenceType"
    const val EXTRA_DAYS_OF_WEEK = "daysOfWeek"
    const val EXTRA_START_DATE = "startDate"
    const val EXTRA_END_DATE = "endDate"

    fun todayDateString(): String {
      val cal = Calendar.getInstance()
      return String.format(
        Locale.US,
        "%04d-%02d-%02d",
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.DAY_OF_MONTH)
      )
    }

    private fun addDays(dateStr: String, days: Int): String {
      val (y, m, d) = parseDateParts(dateStr) ?: return dateStr
      val cal = Calendar.getInstance().apply {
        set(y, m - 1, d)
        add(Calendar.DAY_OF_YEAR, days)
      }
      return String.format(Locale.US, "%04d-%02d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    }

    private fun parseDateParts(dateStr: String): Triple<Int, Int, Int>? {
      val parts = dateStr.split("-")
      if (parts.size != 3) return null
      return try {
        Triple(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
      } catch (e: NumberFormatException) {
        null
      }
    }

    private fun daysInMonth(year: Int, month1: Int): Int {
      val cal = Calendar.getInstance()
      cal.set(year, month1 - 1, 1)
      return cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    /** Days are stored 0=Sun..6=Sat (JS convention); Calendar.DAY_OF_WEEK is 1=Sun..7=Sat,
     * hence the -1. */
    private fun weekdayOf(dateStr: String): Int? {
      val (y, m, d) = parseDateParts(dateStr) ?: return null
      val cal = Calendar.getInstance().apply { set(y, m - 1, d) }
      return cal.get(Calendar.DAY_OF_WEEK) - 1
    }

    fun isActiveOn(
      recurrenceType: String,
      daysOfWeek: String,
      startDate: String,
      endDate: String,
      dateStr: String
    ): Boolean {
      if (startDate.isNotBlank() && dateStr < startDate) return false
      if (endDate.isNotBlank() && dateStr > endDate) return false

      return when (recurrenceType) {
        "weekly" -> {
          if (daysOfWeek.isBlank()) return false
          val weekday = weekdayOf(dateStr) ?: return true
          daysOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }.contains(weekday)
        }
        "monthly" -> {
          if (startDate.isBlank()) return false
          val anchorDay = parseDateParts(startDate)?.third ?: return false
          val (y, m, d) = parseDateParts(dateStr) ?: return false
          val target = minOf(anchorDay, daysInMonth(y, m))
          d == target
        }
        // "daily", and any unrecognized value fails open rather than silently going quiet.
        else -> true
      }
    }
  }

  override fun onReceive(context: Context, intent: Intent) {
    val requestCode = intent.getIntExtra(EXTRA_REQUEST_CODE, -1)
    val scheduleId = intent.getIntExtra(EXTRA_SCHEDULE_ID, -1)
    val medicationId = intent.getIntExtra(EXTRA_MEDICATION_ID, -1)
    val medicationName = intent.getStringExtra(EXTRA_MEDICATION_NAME) ?: "your medication"
    val dosage = intent.getStringExtra(EXTRA_DOSAGE)
    val isPrimary = intent.getBooleanExtra(EXTRA_IS_PRIMARY, false)
    val recurrenceType = intent.getStringExtra(EXTRA_RECURRENCE_TYPE) ?: "daily"
    val daysOfWeek = intent.getStringExtra(EXTRA_DAYS_OF_WEEK) ?: ""
    val startDate = intent.getStringExtra(EXTRA_START_DATE) ?: ""
    val endDate = intent.getStringExtra(EXTRA_END_DATE) ?: ""

    // Chain B (snooze follow-up) always rings — the user already engaged today. Chain A
    // (daily poll) only rings on days the recurrence rule matches; the rearm below still
    // happens regardless (until past the end date) so the next active day isn't affected.
    if (!isPrimary || isActiveOn(recurrenceType, daysOfWeek, startDate, endDate, todayDateString())) {
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
      // Once past the end date there's nothing left to ring for — let the chain terminate
      // rather than polling forever.
      if (endDate.isBlank() || addDays(todayDateString(), 1) <= endDate) {
        rearmNextDay(
          context,
          requestCode,
          scheduleId,
          medicationId,
          medicationName,
          dosage,
          hour,
          minute,
          recurrenceType,
          daysOfWeek,
          startDate,
          endDate
        )
      }
    }
  }

  private fun rearmNextDay(
    context: Context,
    requestCode: Int,
    scheduleId: Int,
    medicationId: Int,
    medicationName: String,
    dosage: String?,
    hour: Int,
    minute: Int,
    recurrenceType: String,
    daysOfWeek: String,
    startDate: String,
    endDate: String
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
      putExtra(EXTRA_RECURRENCE_TYPE, recurrenceType)
      putExtra(EXTRA_DAYS_OF_WEEK, daysOfWeek)
      putExtra(EXTRA_START_DATE, startDate)
      putExtra(EXTRA_END_DATE, endDate)
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
