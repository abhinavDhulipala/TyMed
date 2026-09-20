package expo.modules.tymedalarm

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

/**
 * Native full-screen takeover shown while an alarm rings — launched via the ring service's
 * full-screen-intent notification, so it reliably appears even over a locked screen with a
 * cold JS context. Only Taken/Snooze can dismiss it (back button is disabled), matching a
 * real alarm clock rather than a normal dismissible notification.
 */
class AlarmActivity : AppCompatActivity() {
  private var requestCode = -1
  private var scheduleId = -1
  private var medicationId = -1

  private val tickHandler = Handler(Looper.getMainLooper())
  private var ringingSinceMillis = 0L
  private var elapsedLabel: TextView? = null

  private val tickRunnable = object : Runnable {
    override fun run() {
      elapsedLabel?.text = formatElapsed(System.currentTimeMillis() - ringingSinceMillis)
      tickHandler.postDelayed(this, 1000)
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    showOverLockScreen()

    requestCode = intent.getIntExtra(AlarmReceiver.EXTRA_REQUEST_CODE, -1)
    scheduleId = intent.getIntExtra(AlarmReceiver.EXTRA_SCHEDULE_ID, -1)
    medicationId = intent.getIntExtra(AlarmReceiver.EXTRA_MEDICATION_ID, -1)
    val medicationName = intent.getStringExtra(AlarmReceiver.EXTRA_MEDICATION_NAME) ?: "your medication"
    val dosage = intent.getStringExtra(AlarmReceiver.EXTRA_DOSAGE)

    setContentView(buildLayout(medicationName, dosage))

    ringingSinceMillis = System.currentTimeMillis()
    tickHandler.post(tickRunnable)
  }

  override fun onDestroy() {
    tickHandler.removeCallbacks(tickRunnable)
    super.onDestroy()
  }

  private fun formatElapsed(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "Ringing for %d:%02d", minutes, seconds)
  }

  private fun showOverLockScreen() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
      setShowWhenLocked(true)
      setTurnScreenOn(true)
      val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
      keyguardManager.requestDismissKeyguard(this, null)
    } else {
      @Suppress("DEPRECATION")
      window.addFlags(
        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
          WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
          WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
      )
    }
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  }

  private fun buildLayout(medicationName: String, dosage: String?): LinearLayout {
    val root = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER
      setBackgroundColor(Color.parseColor("#D97742"))
      setPadding(64, 64, 64, 64)
    }

    root.addView(
      ImageView(this).apply {
        setImageResource(R.drawable.ic_mascot)
        val size = (96 * resources.displayMetrics.density).toInt()
        layoutParams = LinearLayout.LayoutParams(size, size).apply {
          gravity = Gravity.CENTER
        }
      }
    )

    root.addView(
      TextView(this).apply {
        text = "Time for $medicationName"
        setTextColor(Color.WHITE)
        textSize = 28f
        gravity = Gravity.CENTER
        setPadding(0, 32, 0, 0)
      }
    )

    if (!dosage.isNullOrBlank()) {
      root.addView(
        TextView(this).apply {
          text = dosage
          setTextColor(Color.WHITE)
          textSize = 18f
          gravity = Gravity.CENTER
          setPadding(0, 24, 0, 0)
        }
      )
    }

    elapsedLabel = TextView(this).apply {
      text = formatElapsed(0)
      setTextColor(Color.WHITE)
      alpha = 0.85f
      textSize = 15f
      gravity = Gravity.CENTER
      setPadding(0, 40, 0, 0)
    }
    root.addView(elapsedLabel)

    val buttonRow = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER
      setPadding(0, 96, 0, 0)
    }

    buttonRow.addView(
      Button(this).apply {
        text = "Taken"
        setOnClickListener { onTaken() }
      }
    )

    buttonRow.addView(
      Button(this).apply {
        text = "Snooze"
        setPadding(48, 0, 0, 0)
        setOnClickListener { onSnooze() }
      }
    )

    root.addView(buttonRow)
    return root
  }

  private fun onTaken() {
    stopRingingService()
    cancelSnoozeChain()
    launchApp("taken")
    finish()
  }

  private fun onSnooze() {
    stopRingingService()
    armSnooze()
    launchApp("snooze")
    finish()
  }

  private fun stopRingingService() {
    val stopIntent = Intent(this, AlarmRingService::class.java).apply {
      action = AlarmRingService.ACTION_STOP
    }
    startService(stopIntent)
  }

  private fun cancelSnoozeChain() {
    val snoozeRequestCode = requestCode + SNOOZE_REQUEST_CODE_OFFSET
    val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val fireIntent = Intent(this, AlarmReceiver::class.java)
    val operation = PendingIntent.getBroadcast(
      this,
      snoozeRequestCode,
      fireIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    alarmManager.cancel(operation)
    operation.cancel()
  }

  private fun armSnooze() {
    val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val minutes = prefs.getInt(PREF_FOLLOW_UP_MINUTES, 5)
    val triggerAt = System.currentTimeMillis() + minutes * 60_000L
    val snoozeRequestCode = requestCode + SNOOZE_REQUEST_CODE_OFFSET

    val medicationName = intent.getStringExtra(AlarmReceiver.EXTRA_MEDICATION_NAME) ?: "your medication"
    val dosage = intent.getStringExtra(AlarmReceiver.EXTRA_DOSAGE)

    val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val fireIntent = Intent(this, AlarmReceiver::class.java).apply {
      putExtra(AlarmReceiver.EXTRA_REQUEST_CODE, snoozeRequestCode)
      putExtra(AlarmReceiver.EXTRA_SCHEDULE_ID, scheduleId)
      putExtra(AlarmReceiver.EXTRA_MEDICATION_ID, medicationId)
      putExtra(AlarmReceiver.EXTRA_MEDICATION_NAME, medicationName)
      putExtra(AlarmReceiver.EXTRA_DOSAGE, dosage)
      putExtra(AlarmReceiver.EXTRA_IS_PRIMARY, false)
    }
    val operation = PendingIntent.getBroadcast(
      this,
      snoozeRequestCode,
      fireIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val showIntent = packageManager.getLaunchIntentForPackage(packageName) ?: Intent()
    val showPendingIntent = PendingIntent.getActivity(
      this,
      snoozeRequestCode,
      showIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showPendingIntent), operation)
  }

  private fun launchApp(action: String) {
    val uri = Uri.parse("tymed://dose-action?action=$action&scheduleId=$scheduleId&medicationId=$medicationId")
    val launchIntent = Intent(Intent.ACTION_VIEW, uri).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      setPackage(packageName)
    }
    startActivity(launchIntent)
  }

  @Deprecated("Deprecated in Java")
  override fun onBackPressed() {
    // Intentionally does nothing — alarm can only be dismissed via Taken/Snooze.
  }

  companion object {
    const val SNOOZE_REQUEST_CODE_OFFSET = 500_000
  }
}
