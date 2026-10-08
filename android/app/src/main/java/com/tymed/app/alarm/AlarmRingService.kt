package com.tymed.app.alarm

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
import android.util.Log

/**
 * Foreground service that actually rings: loops alarm-stream audio and vibrates continuously
 * until told to stop (Taken/Snooze tapped in [AlarmActivity]). Also posts the full-screen-intent
 * notification that reliably shows AlarmActivity over the lock screen.
 */
class AlarmRingService : Service() {
    companion object {
        const val ACTION_RING = "com.tymed.app.alarm.ACTION_RING"
        const val ACTION_STOP = "com.tymed.app.alarm.ACTION_STOP"
        private const val TAG = "AlarmRingService"
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
        val profileId = intent?.getIntExtra(AlarmReceiver.EXTRA_PROFILE_ID, -1) ?: -1
        val medicationName = intent?.getStringExtra(AlarmReceiver.EXTRA_MEDICATION_NAME) ?: "your medication"
        val dosage = intent?.getStringExtra(AlarmReceiver.EXTRA_DOSAGE)
        val ringingSinceMillis = intent?.getLongExtra(AlarmReceiver.EXTRA_RINGING_SINCE_MILLIS, -1L)
            ?.takeIf { it > 0 } ?: System.currentTimeMillis()

        ensureAlarmChannel(this)
        val notification = buildAlarmNotification(
            this,
            requestCode,
            scheduleId,
            medicationId,
            profileId,
            medicationName,
            dosage,
            ringingSinceMillis,
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(ALARM_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(ALARM_NOTIFICATION_ID, notification)
            }
        } catch (error: Exception) {
            // The OS can refuse to promote this to a foreground service (e.g. background-start
            // restrictions on a process that's never run since a reboot) — fall back to a plain
            // notification rather than letting the exception crash the whole app.
            Log.w(TAG, "startForeground rejected, falling back to a plain notification", error)
            postFallbackAlarmNotification(
                this,
                requestCode,
                scheduleId,
                medicationId,
                profileId,
                medicationName,
                dosage,
                ringingSinceMillis,
            )
            stopSelf()
            return
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
                        .build(),
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
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

    override fun onDestroy() {
        stopRinging()
        super.onDestroy()
    }
}
