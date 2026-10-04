package com.tymed.app.alarm

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class RingingAlarmInfo(
    val requestCode: Int,
    val scheduleId: Int,
    val medicationId: Int,
    val medicationName: String,
    val dosage: String?,
    val ringingSinceMillis: Long,
)

/**
 * In-memory record of the alarm currently ringing (if any), set by [AlarmReceiver] when it starts
 * the ring service / fallback notification and cleared only when [AlarmActivity] resolves it via
 * Taken or Snooze. Lets [com.tymed.app.MainActivity] re-show [AlarmActivity] when the app is
 * opened some other way than tapping the notification — e.g. the launcher icon — while a dose
 * alert is still firing. Deliberately process-local (no Room/DataStore persistence): it mirrors
 * the live `AlarmManager`/service state, which is itself gone the moment the process dies.
 */
object RingingAlarmTracker {
    private val _current = MutableStateFlow<RingingAlarmInfo?>(null)
    val current: StateFlow<RingingAlarmInfo?> = _current

    fun start(info: RingingAlarmInfo) {
        _current.value = info
    }

    fun clear() {
        _current.value = null
    }
}
