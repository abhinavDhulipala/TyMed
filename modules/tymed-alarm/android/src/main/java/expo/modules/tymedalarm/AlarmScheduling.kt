package expo.modules.tymedalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** Plain (non-Expo-Record) counterpart of [AlarmRequest] so the same arming logic can be
 * shared between the JS-facing [TymedAlarmModule] and [BootReceiver], which has no JS runtime
 * to hand it an Expo Record. */
data class AlarmSchedule(
  val triggerAtMillis: Long,
  val requestCode: Int,
  val scheduleId: Int,
  val medicationId: Int,
  val medicationName: String,
  val dosage: String?,
  val isPrimary: Boolean,
  val hour: Int,
  val minute: Int,
  val recurrenceType: String,
  val daysOfWeek: String,
  val startDate: String,
  val endDate: String
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
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
  )

  val showIntent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent()
  val showPendingIntent = PendingIntent.getActivity(
    context,
    schedule.requestCode,
    showIntent,
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
  )

  alarmManager.setAlarmClock(
    AlarmManager.AlarmClockInfo(schedule.triggerAtMillis, showPendingIntent),
    operation
  )
}
