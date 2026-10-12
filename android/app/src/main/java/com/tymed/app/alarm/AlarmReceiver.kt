package com.tymed.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.tymed.app.data.RecurrenceRule
import com.tymed.app.util.todayDateString
import java.time.LocalDate

/**
 * Fires when a Chain A (daily dose) or Chain B (snooze follow-up) alarm goes off. Always starts
 * the ringing foreground service. Chain A alarms additionally re-arm themselves for the same
 * time the next day, so the daily poll is self-perpetuating and independent of whether the app
 * is running or the dose is ever marked taken — whether it actually *rings* that day is gated by
 * [RecurrenceRule.isActiveOn], the single shared recurrence evaluator also used by the UI's
 * doses-for-date logic (no more hand-synced TS/Kotlin duplicate).
 */
class AlarmReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "AlarmReceiver"
        const val EXTRA_REQUEST_CODE = "requestCode"
        const val EXTRA_SCHEDULE_ID = "scheduleId"
        const val EXTRA_MEDICATION_ID = "medicationId"
        const val EXTRA_PROFILE_ID = "profileId"
        const val EXTRA_MEDICATION_NAME = "medicationName"
        const val EXTRA_DOSAGE = "dosage"
        const val EXTRA_IS_PRIMARY = "isPrimary"
        const val EXTRA_HOUR = "hour"
        const val EXTRA_MINUTE = "minute"
        const val EXTRA_RECURRENCE_TYPE = "recurrenceType"
        const val EXTRA_DAYS_OF_WEEK = "daysOfWeek"
        const val EXTRA_START_DATE = "startDate"
        const val EXTRA_END_DATE = "endDate"
        const val EXTRA_RINGING_SINCE_MILLIS = "ringingSinceMillis"

        private fun addDays(dateStr: String, days: Int): String = LocalDate.parse(dateStr).plusDays(days.toLong()).toString()

        fun isActiveOn(recurrenceType: String, daysOfWeekCsv: String, startDate: String, endDate: String, dateStr: String): Boolean {
            val daysOfWeek = if (daysOfWeekCsv.isBlank()) {
                null
            } else {
                daysOfWeekCsv.split(",").mapNotNull { it.trim().toIntOrNull() }
            }
            val rule = RecurrenceRule(
                recurrenceType = recurrenceType,
                daysOfWeek = daysOfWeek,
                startDate = startDate.ifBlank { null },
                endDate = endDate.ifBlank { null },
            )
            return RecurrenceRule.isActiveOn(rule, dateStr)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val requestCode = intent.getIntExtra(EXTRA_REQUEST_CODE, -1)
        val scheduleId = intent.getIntExtra(EXTRA_SCHEDULE_ID, -1)
        val medicationId = intent.getIntExtra(EXTRA_MEDICATION_ID, -1)
        val profileId = intent.getIntExtra(EXTRA_PROFILE_ID, -1)
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
            val ringingSinceMillis = System.currentTimeMillis()
            RingingAlarmTracker.start(
                RingingAlarmInfo(
                    requestCode = requestCode,
                    scheduleId = scheduleId,
                    medicationId = medicationId,
                    profileId = profileId,
                    medicationName = medicationName,
                    dosage = dosage,
                    ringingSinceMillis = ringingSinceMillis,
                ),
            )

            val serviceIntent = Intent(context, AlarmRingService::class.java).apply {
                action = AlarmRingService.ACTION_RING
                putExtra(EXTRA_REQUEST_CODE, requestCode)
                putExtra(EXTRA_SCHEDULE_ID, scheduleId)
                putExtra(EXTRA_MEDICATION_ID, medicationId)
                putExtra(EXTRA_PROFILE_ID, profileId)
                putExtra(EXTRA_MEDICATION_NAME, medicationName)
                putExtra(EXTRA_DOSAGE, dosage)
                putExtra(EXTRA_RINGING_SINCE_MILLIS, ringingSinceMillis)
            }
            try {
                // startForegroundService() itself requires API 26; below that, a plain
                // startService() is enough — the service calls Service#startForeground (API 5+)
                // itself once it's running.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (error: Exception) {
                // The OS can refuse this outright (e.g. background-start restrictions on a
                // process that's never run since a reboot) — fall back to a plain notification
                // rather than letting the exception crash the whole app.
                Log.w(TAG, "startForegroundService rejected, falling back to a plain notification", error)
                postFallbackAlarmNotification(
                    context,
                    requestCode,
                    scheduleId,
                    medicationId,
                    profileId,
                    medicationName,
                    dosage,
                    ringingSinceMillis,
                )
            }
        }

        if (isPrimary) {
            val hour = intent.getIntExtra(EXTRA_HOUR, 0)
            val minute = intent.getIntExtra(EXTRA_MINUTE, 0)
            // Once past the end date there's nothing left to ring for — let the chain
            // terminate rather than polling forever.
            if (endDate.isBlank() || addDays(todayDateString(), 1) <= endDate) {
                rearmNextDay(
                    context,
                    requestCode,
                    scheduleId,
                    medicationId,
                    profileId,
                    medicationName,
                    dosage,
                    hour,
                    minute,
                    recurrenceType,
                    daysOfWeek,
                    startDate,
                    endDate,
                )
            }
        }
    }

    private fun rearmNextDay(
        context: Context,
        requestCode: Int,
        scheduleId: Int,
        medicationId: Int,
        profileId: Int,
        medicationName: String,
        dosage: String?,
        hour: Int,
        minute: Int,
        recurrenceType: String,
        daysOfWeek: String,
        startDate: String,
        endDate: String,
    ) {
        // Anchored to hour:minute (via tomorrowOccurrenceMillis, using Calendar) rather than
        // "now + 24h" so a late-firing alarm (Doze/battery optimization) doesn't cause the
        // daily time to drift.
        armAlarm(
            context,
            AlarmSchedule(
                triggerAtMillis = tomorrowOccurrenceMillis(hour, minute),
                requestCode = requestCode,
                scheduleId = scheduleId,
                medicationId = medicationId,
                profileId = profileId,
                medicationName = medicationName,
                dosage = dosage,
                isPrimary = true,
                hour = hour,
                minute = minute,
                recurrenceType = recurrenceType,
                daysOfWeek = daysOfWeek,
                startDate = startDate,
                endDate = endDate,
            ),
        )
    }
}
