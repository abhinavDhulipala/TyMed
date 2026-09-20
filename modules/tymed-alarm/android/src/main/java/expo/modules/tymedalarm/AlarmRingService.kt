package expo.modules.tymedalarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat

/**
 * Foreground service that actually rings: loops alarm-stream audio and vibrates
 * continuously until told to stop (Taken/Snooze tapped in AlarmActivity). Also posts the
 * full-screen-intent notification that reliably shows AlarmActivity over the lock screen.
 */
class AlarmRingService : Service() {
  companion object {
    const val ACTION_RING = "expo.modules.tymedalarm.ACTION_RING"
    const val ACTION_STOP = "expo.modules.tymedalarm.ACTION_STOP"
    const val CHANNEL_ID = "tymed-alarm-ring"
    const val NOTIFICATION_ID = 9721
  }

  private var mediaPlayer: MediaPlayer? = null
  private var vibrator: Vibrator? = null

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      stopRinging()
      stopForeground(STOP_FOREGROUND_REMOVE)
      stopSelf()
    } else {
      startRinging(intent)
    }
    return START_NOT_STICKY
  }

  private fun startRinging(intent: Intent?) {
    val requestCode = intent?.getIntExtra(AlarmReceiver.EXTRA_REQUEST_CODE, -1) ?: -1
    val scheduleId = intent?.getIntExtra(AlarmReceiver.EXTRA_SCHEDULE_ID, -1) ?: -1
    val medicationId = intent?.getIntExtra(AlarmReceiver.EXTRA_MEDICATION_ID, -1) ?: -1
    val medicationName = intent?.getStringExtra(AlarmReceiver.EXTRA_MEDICATION_NAME) ?: "your medication"
    val dosage = intent?.getStringExtra(AlarmReceiver.EXTRA_DOSAGE)

    ensureChannel()

    val fullScreenIntent = Intent(this, AlarmActivity::class.java).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
      putExtra(AlarmReceiver.EXTRA_REQUEST_CODE, requestCode)
      putExtra(AlarmReceiver.EXTRA_SCHEDULE_ID, scheduleId)
      putExtra(AlarmReceiver.EXTRA_MEDICATION_ID, medicationId)
      putExtra(AlarmReceiver.EXTRA_MEDICATION_NAME, medicationName)
      putExtra(AlarmReceiver.EXTRA_DOSAGE, dosage)
    }
    val fullScreenPendingIntent = PendingIntent.getActivity(
      this,
      requestCode,
      fullScreenIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentTitle("Time for $medicationName")
      .setContentText(dosage?.let { "Dose: $it" } ?: "Time to take your dose")
      .setSmallIcon(applicationInfo.icon)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setCategory(NotificationCompat.CATEGORY_ALARM)
      .setFullScreenIntent(fullScreenPendingIntent, true)
      .setContentIntent(fullScreenPendingIntent)
      .setOngoing(true)
      .build()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }

    startAudioAndVibration()
  }

  private fun startAudioAndVibration() {
    stopRinging()

    try {
      val alarmUri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

      mediaPlayer = MediaPlayer().apply {
        setAudioAttributes(
          AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        )
        setDataSource(this@AlarmRingService, alarmUri)
        isLooping = true
        setOnPreparedListener { it.start() }
        prepareAsync()
      }
    } catch (error: Exception) {
      // No alarm sound available on this device/build — vibration alone still alerts.
    }

    val pattern = longArrayOf(0, 800, 400, 800, 400)
    vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
      @Suppress("DEPRECATION")
      getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
  }

  private fun stopRinging() {
    mediaPlayer?.apply {
      try {
        stop()
      } catch (error: Exception) {
        // Already stopped/released — nothing to do.
      }
      release()
    }
    mediaPlayer = null
    vibrator?.cancel()
  }

  private fun ensureChannel() {
    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (manager.getNotificationChannel(CHANNEL_ID) == null) {
      val channel = NotificationChannel(CHANNEL_ID, "Alarm", NotificationManager.IMPORTANCE_HIGH).apply {
        description = "Full-screen medication alarms"
        // The looping MediaPlayer handles sound; a channel sound too would double up.
        setSound(null, null)
        enableVibration(false)
      }
      manager.createNotificationChannel(channel)
    }
  }

  override fun onDestroy() {
    stopRinging()
    super.onDestroy()
  }
}
