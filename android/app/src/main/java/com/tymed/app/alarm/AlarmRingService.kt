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
import androidx.core.app.NotificationManagerCompat

/**
 * Foreground service that actually rings: loops alarm-stream audio and vibrates continuously
 * until told to stop (Taken/Snooze tapped in [AlarmActivity]). Also posts the full-screen-intent
 * notification that reliably shows AlarmActivity over the lock screen.
 *
 * Tracks every ringing alarm by request code, not just one — two different profiles' doses can
 * legitimately be due at the same moment on a shared device, and one of them firing must never
 * silence or hide the other. Each gets its own [MediaPlayer] and its own notification id; the
 * vibrator is the one piece that's genuinely shared hardware, so a second alarm just re-triggers
 * the same continuous pattern rather than getting a meaningless "own" vibration.
 */
class AlarmRingService : Service() {
    companion object {
        const val ACTION_RING = "com.tymed.app.alarm.ACTION_RING"
        const val ACTION_STOP = "com.tymed.app.alarm.ACTION_STOP"
        private const val TAG = "AlarmRingService"
    }

    private val ringingRequestCodes = mutableSetOf<Int>()
    private val mediaPlayers = mutableMapOf<Int, MediaPlayer>()
    private var vibrator: Vibrator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopOne(intent.getIntExtra(AlarmReceiver.EXTRA_REQUEST_CODE, -1))
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

        ringingRequestCodes += requestCode

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
            // Re-anchoring the foreground notification to this alarm doesn't remove whichever
            // other alarm's notification is already posted under its own id — it just becomes
            // the one the service is currently "backed by" for the foreground contract.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(requestCode, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(requestCode, notification)
            }
        } catch (error: Exception) {
            // The OS can refuse to promote this to a foreground service (e.g. background-start
            // restrictions on a process that's never run since a reboot) — fall back to a plain
            // notification rather than letting the exception crash the whole app.
            Log.w(TAG, "startForeground rejected, falling back to a plain notification", error)
            ringingRequestCodes -= requestCode
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
            // Only tear the whole service down if nothing else is genuinely still ringing
            // through it — a different alarm may already be ringing successfully.
            if (ringingRequestCodes.isEmpty()) stopSelf()
            return
        }

        startAudio(requestCode)
        startVibration()
    }

    /** Each ringing alarm gets its own player so dismissing or re-arming one can never stop
     * another's sound. */
    private fun startAudio(requestCode: Int) {
        stopAudio(requestCode) // defensive: a stray duplicate RING for the same request code

        try {
            val alarmUri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

            mediaPlayers[requestCode] = MediaPlayer().apply {
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
    }

    private fun startVibration() {
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

    private fun stopAudio(requestCode: Int) {
        mediaPlayers.remove(requestCode)?.apply {
            try {
                stop()
            } catch (error: Exception) {
                // Already stopped/released — nothing to do.
            }
            release()
        }
    }

    /** Stops exactly one ringing alarm — its sound and its own notification — leaving any other
     * still-ringing alarm untouched. Only tears down the service's foreground state and
     * vibration once nothing is left ringing through it. */
    private fun stopOne(requestCode: Int) {
        ringingRequestCodes -= requestCode
        stopAudio(requestCode)
        NotificationManagerCompat.from(this).cancel(requestCode)
        if (ringingRequestCodes.isEmpty()) {
            vibrator?.cancel()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaPlayers.keys.toList().forEach(::stopAudio)
        ringingRequestCodes.clear()
        vibrator?.cancel()
        super.onDestroy()
    }
}
