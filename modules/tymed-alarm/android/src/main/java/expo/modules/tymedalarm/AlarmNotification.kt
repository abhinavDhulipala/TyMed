package expo.modules.tymedalarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

const val ALARM_CHANNEL_ID = "tymed-alarm-ring"
const val ALARM_NOTIFICATION_ID = 9721

/** Shared by [AlarmRingService] (the normal ringing path) and [postFallbackAlarmNotification]
 * (used when the OS refuses to start that foreground service) — both need the exact same
 * full-screen-intent notification, just with or without an active service behind it. */
fun ensureAlarmChannel(context: Context) {
  val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
  if (manager.getNotificationChannel(ALARM_CHANNEL_ID) == null) {
    val channel = NotificationChannel(ALARM_CHANNEL_ID, "Alarm", NotificationManager.IMPORTANCE_HIGH).apply {
      description = "Full-screen medication alarms"
      // The looping MediaPlayer handles sound when the ring service is running; a channel
      // sound too would double up. The fallback notification path has no sound of its own,
      // but re-enabling it here would only affect future channel creation, not this one.
      setSound(null, null)
      enableVibration(false)
    }
    manager.createNotificationChannel(channel)
  }
}

fun buildAlarmNotification(
  context: Context,
  requestCode: Int,
  scheduleId: Int,
  medicationId: Int,
  medicationName: String,
  dosage: String?
): Notification {
  val fullScreenIntent = Intent(context, AlarmActivity::class.java).apply {
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    putExtra(AlarmReceiver.EXTRA_REQUEST_CODE, requestCode)
    putExtra(AlarmReceiver.EXTRA_SCHEDULE_ID, scheduleId)
    putExtra(AlarmReceiver.EXTRA_MEDICATION_ID, medicationId)
    putExtra(AlarmReceiver.EXTRA_MEDICATION_NAME, medicationName)
    putExtra(AlarmReceiver.EXTRA_DOSAGE, dosage)
  }
  val fullScreenPendingIntent = PendingIntent.getActivity(
    context,
    requestCode,
    fullScreenIntent,
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
  )

  return NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
    .setContentTitle("Time for $medicationName")
    .setContentText(dosage?.let { "Dose: $it" } ?: "Time to take your dose")
    .setSmallIcon(context.applicationInfo.icon)
    .setPriority(NotificationCompat.PRIORITY_HIGH)
    .setCategory(NotificationCompat.CATEGORY_ALARM)
    .setFullScreenIntent(fullScreenPendingIntent, true)
    .setContentIntent(fullScreenPendingIntent)
    .setOngoing(true)
    .build()
}

/** Posts the alarm notification directly, without an active [AlarmRingService] behind it.
 * Used when the OS refuses to start that foreground service — e.g. background-start
 * restrictions rejecting it on a process that's never run since a reboot (see [AlarmReceiver]
 * and [AlarmRingService]'s callers). No looping sound/vibration this way, but the full-screen
 * takeover and its Taken/Snooze actions still work, so a dose alert degrades instead of the
 * whole app crashing. */
fun postFallbackAlarmNotification(
  context: Context,
  requestCode: Int,
  scheduleId: Int,
  medicationId: Int,
  medicationName: String,
  dosage: String?
) {
  ensureAlarmChannel(context)
  val notification = buildAlarmNotification(context, requestCode, scheduleId, medicationId, medicationName, dosage)
  NotificationManagerCompat.from(context).notify(ALARM_NOTIFICATION_ID, notification)
}
