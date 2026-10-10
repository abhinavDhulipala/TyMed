package com.tymed.app.alarm

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class RingingAlarmInfo(
    val requestCode: Int,
    val scheduleId: Int,
    val medicationId: Int,
    val profileId: Int,
    val medicationName: String,
    val dosage: String?,
    val ringingSinceMillis: Long,
)

/**
 * In-memory record of every alarm currently ringing, keyed by request code — set by
 * [AlarmReceiver] when it starts the ring service / fallback notification for one, and cleared
 * only when [AlarmActivity] resolves that specific one via Taken or Snooze. A list, not a single
 * slot: two different profiles' doses can legitimately be due at the same moment on a shared
 * device, and resolving one must never make the app forget another is still ringing. Lets
 * [com.tymed.app.MainActivity] re-show [AlarmActivity] when the app is opened some other way than
 * tapping a notification — e.g. the launcher icon — while a dose alert is still firing.
 * Deliberately process-local (no Room/DataStore persistence): it mirrors the live
 * `AlarmManager`/service state, which is itself gone the moment the process dies.
 */
object RingingAlarmTracker {
    private val _current = MutableStateFlow<List<RingingAlarmInfo>>(emptyList())
    val current: StateFlow<List<RingingAlarmInfo>> = _current

    fun start(info: RingingAlarmInfo) {
        _current.update { list -> list.filterNot { it.requestCode == info.requestCode } + info }
    }

    fun clear(requestCode: Int) {
        _current.update { list -> list.filterNot { it.requestCode == requestCode } }
    }
}

/** Builds the launch intent for [AlarmActivity] to show a given ringing alarm — shared by
 * [com.tymed.app.MainActivity] (re-showing one when the app opens some other way than tapping its
 * notification) and [AlarmActivity] itself (chaining to the next still-ringing alarm once the one
 * on screen is resolved). */
fun ringingAlarmIntent(context: Context, info: RingingAlarmInfo): Intent =
    Intent(context, AlarmActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        putExtra(AlarmReceiver.EXTRA_REQUEST_CODE, info.requestCode)
        putExtra(AlarmReceiver.EXTRA_SCHEDULE_ID, info.scheduleId)
        putExtra(AlarmReceiver.EXTRA_MEDICATION_ID, info.medicationId)
        putExtra(AlarmReceiver.EXTRA_PROFILE_ID, info.profileId)
        putExtra(AlarmReceiver.EXTRA_MEDICATION_NAME, info.medicationName)
        putExtra(AlarmReceiver.EXTRA_DOSAGE, info.dosage)
        putExtra(AlarmReceiver.EXTRA_RINGING_SINCE_MILLIS, info.ringingSinceMillis)
    }
