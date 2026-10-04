package com.tymed.app.alarm

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

const val ALARM_CHANNEL_ID = "tymed-alarm-ring"
const val ALARM_NOTIFICATION_ID = 9721

/** Shared by [AlarmRingService] (the normal ringing path) and [postFallbackAlarmNotification]
 * (used when the OS refuses to start that foreground service) — both need the exact same
 * full-screen-intent notification, just with or without an active service behind it.
 * Notification channels don't exist before API 26 (minSdk here is 24) — a notification posted
 * with a channel id that doesn't exist on those OS versions just posts without channel-level
 * controls, so this is a no-op rather than a gate on the rest of the alarm flow. */
fun ensureAlarmChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (manager.getNotificationChannel(ALARM_CHANNEL_ID) == null) {
        val channel = NotificationChannel(ALARM_CHANNEL_ID, "Alarm", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Full-screen medication alarms"
            // The looping MediaPlayer handles sound when the ring service is running; a
            // channel sound too would double up.
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
    dosage: String?,
    ringingSinceMillis: Long,
): Notification {
    val fullScreenIntent = Intent(context, AlarmActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        putExtra(AlarmReceiver.EXTRA_REQUEST_CODE, requestCode)
        putExtra(AlarmReceiver.EXTRA_SCHEDULE_ID, scheduleId)
        putExtra(AlarmReceiver.EXTRA_MEDICATION_ID, medicationId)
        putExtra(AlarmReceiver.EXTRA_MEDICATION_NAME, medicationName)
        putExtra(AlarmReceiver.EXTRA_DOSAGE, dosage)
        putExtra(AlarmReceiver.EXTRA_RINGING_SINCE_MILLIS, ringingSinceMillis)
    }
    val fullScreenPendingIntent = PendingIntent.getActivity(
        context,
        requestCode,
        fullScreenIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
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

/** Posts the alarm notification directly, without an active [AlarmRingService] behind it. Used
 * when the OS refuses to start that foreground service — e.g. background-start restrictions
 * rejecting it on a process that's never run since a reboot. No looping sound/vibration this
 * way, but the full-screen takeover and its Taken/Snooze actions still work, so a dose alert
 * degrades instead of the whole app crashing. */
// POST_NOTIFICATIONS is requested at app startup (see TymedApp.kt); if the user denied it,
// notify() simply doesn't show anything rather than throwing — there's no crash to guard
// against, just nothing lint can verify across files.
@SuppressLint("MissingPermission")
fun postFallbackAlarmNotification(
    context: Context,
    requestCode: Int,
    scheduleId: Int,
    medicationId: Int,
    medicationName: String,
    dosage: String?,
    ringingSinceMillis: Long,
) {
    ensureAlarmChannel(context)
    val notification = buildAlarmNotification(
        context,
        requestCode,
        scheduleId,
        medicationId,
        medicationName,
        dosage,
        ringingSinceMillis,
    )
    NotificationManagerCompat.from(context).notify(ALARM_NOTIFICATION_ID, notification)
}
