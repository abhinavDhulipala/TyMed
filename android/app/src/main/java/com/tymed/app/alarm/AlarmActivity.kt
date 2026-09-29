package com.tymed.app.alarm

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.lifecycle.lifecycleScope
import com.tymed.app.MainActivity
import com.tymed.app.TymedApplication
import com.tymed.app.data.entity.DoseStatus
import com.tymed.app.observability.captureException
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Native full-screen takeover shown while an alarm rings — launched via the ring service's
 * full-screen-intent notification, so it reliably appears even over a locked screen. Only
 * Taken/Snooze can dismiss it (back button is disabled), matching a real alarm clock rather than
 * a normal dismissible notification.
 *
 * The Thyme mascot counts the ring down from a full 5:00 to 0:00, swaying gently the whole time
 * while its smile eases into a frown as the countdown progresses. Ringing past 5 minutes just
 * holds at the full-frown state rather than going idle.
 */
class AlarmActivity : ComponentActivity() {
    private var requestCode = -1
    private var scheduleId = -1
    private var medicationId = -1

    private val tickHandler = Handler(Looper.getMainLooper())
    private var ringingSinceMillis = 0L
    private var remainingLabel: TextView? = null
    private var mascotView: MascotView? = null

    private val tickRunnable = object : Runnable {
        override fun run() {
            val elapsed = System.currentTimeMillis() - ringingSinceMillis
            val remaining = (COUNTDOWN_MILLIS - elapsed).coerceAtLeast(0)
            remainingLabel?.text = formatRemaining(remaining)
            mascotView?.progress = elapsed.toFloat() / COUNTDOWN_MILLIS
            tickHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Consumes back presses/gestures without finishing the activity — only Taken/Snooze can
        // dismiss it, matching a real alarm clock. onBackPressedDispatcher (not overriding the
        // deprecated onBackPressed()) is what actually intercepts predictive back gestures too.
        onBackPressedDispatcher.addCallback(this) {}

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

    private fun formatRemaining(millis: Long): String {
        val totalSeconds = (millis / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%d:%02d", minutes, seconds)
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
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun buildLayout(medicationName: String, dosage: String?): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor(BACKGROUND_GREEN))
            setPadding(64, 64, 64, 64)
        }

        root.addView(
            MascotView(this).apply {
                val size = (200 * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    gravity = Gravity.CENTER
                }
            }.also { mascotView = it },
        )

        root.addView(
            TextView(this).apply {
                text = "Time for $medicationName"
                setTextColor(Color.WHITE)
                textSize = 28f
                gravity = Gravity.CENTER
                setPadding(0, 32, 0, 0)
            },
        )

        if (!dosage.isNullOrBlank()) {
            root.addView(
                TextView(this).apply {
                    text = dosage
                    setTextColor(Color.WHITE)
                    textSize = 18f
                    gravity = Gravity.CENTER
                    setPadding(0, 24, 0, 0)
                },
            )
        }

        remainingLabel = TextView(this).apply {
            text = formatRemaining(COUNTDOWN_MILLIS)
            setTextColor(Color.WHITE)
            alpha = 0.85f
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, 40, 0, 0)
        }
        root.addView(remainingLabel)

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 96, 0, 0)
        }

        buttonRow.addView(pillButton("Taken", primary = true).apply { setOnClickListener { onTaken() } })
        buttonRow.addView(
            pillButton("Snooze", primary = false).apply {
                layoutParams = (layoutParams as LinearLayout.LayoutParams).apply {
                    marginStart = (20 * resources.displayMetrics.density).toInt()
                }
                setOnClickListener { onSnooze() }
            },
        )

        root.addView(buttonRow)
        return root
    }

    /**
     * A rounded pill button in place of the stock gray Material button, which read as a
     * placeholder rather than a finished screen. Taken is a solid cream fill (the confident,
     * "done" action); Snooze is a ghost outline (present but visually secondary).
     */
    private fun pillButton(label: String, primary: Boolean): Button {
        val density = resources.displayMetrics.density
        val corner = 28f * density
        val fill = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = corner
            if (primary) {
                setColor(Color.parseColor("#FFFBF3"))
            } else {
                setColor(Color.TRANSPARENT)
                setStroke((1.5f * density).toInt(), Color.WHITE)
            }
        }
        val rippleColor = if (primary) Color.parseColor("#332E4A32") else Color.parseColor("#33FFFFFF")
        return Button(this).apply {
            text = label
            typeface = Typeface.DEFAULT_BOLD
            textSize = 16f
            setTextColor(if (primary) Color.parseColor(BACKGROUND_GREEN) else Color.WHITE)
            background = RippleDrawable(ColorStateList.valueOf(rippleColor), fill, null)
            stateListAnimator = null
            val hPad = (30 * density).toInt()
            val vPad = (16 * density).toInt()
            setPadding(hPad, vPad, hPad, vPad)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }
    }

    private fun onTaken() {
        stopRingingService()
        cancelSnoozeChain()
        markTakenThenOpenApp()
    }

    private fun onSnooze() {
        stopRingingService()
        armSnooze()
        openApp()
        finish()
    }

    private fun stopRingingService() {
        val stopIntent = Intent(this, AlarmRingService::class.java).apply {
            action = AlarmRingService.ACTION_STOP
        }
        startService(stopIntent)
    }

    private fun cancelSnoozeChain() {
        cancelAlarm(this, requestCode + SNOOZE_REQUEST_CODE_OFFSET)
    }

    private fun armSnooze() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val minutes = prefs.getInt(PREF_FOLLOW_UP_MINUTES, 5)
        val triggerAt = System.currentTimeMillis() + minutes * 60_000L
        val snoozeRequestCode = requestCode + SNOOZE_REQUEST_CODE_OFFSET

        val medicationName = intent.getStringExtra(AlarmReceiver.EXTRA_MEDICATION_NAME) ?: "your medication"
        val dosage = intent.getStringExtra(AlarmReceiver.EXTRA_DOSAGE)

        armAlarm(
            this,
            AlarmSchedule(
                triggerAtMillis = triggerAt,
                requestCode = snoozeRequestCode,
                scheduleId = scheduleId,
                medicationId = medicationId,
                medicationName = medicationName,
                dosage = dosage,
                isPrimary = false,
                hour = 0,
                minute = 0,
                recurrenceType = "daily",
                daysOfWeek = "",
                startDate = "",
                endDate = "",
            ),
        )
    }

    /** No more deep link back into a JS runtime — there is no JS runtime. Marks the dose
     * directly against the Room database in-process, then opens the app. Wrapped so a failure
     * here never leaves the user stuck on a ringing-adjacent blank screen. */
    private fun markTakenThenOpenApp() {
        val container = (application as TymedApplication).container
        lifecycleScope.launch {
            try {
                val log = container.intakeLogRepository.findOrCreateTodayLogForSchedule(scheduleId.toLong())
                container.doseActions.markDose(log.id, DoseStatus.TAKEN)
            } catch (error: Exception) {
                captureException(error)
            } finally {
                openApp()
                finish()
            }
        }
    }

    private fun openApp() {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(launchIntent)
    }

    companion object {
        private const val COUNTDOWN_MILLIS = 5 * 60 * 1000L
        private const val BACKGROUND_GREEN = "#1F3325"
    }
}
