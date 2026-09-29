package com.tymed.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tymed.app.TymedApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * A reboot (or the user force-stopping the app) wipes every [android.app.AlarmManager] entry the
 * app had armed — including [AlarmReceiver]'s self-perpetuating daily chains — with nothing left
 * to fire and re-arm itself. This re-arms every enabled schedule's next occurrence directly from
 * Room (unlike the old Expo-era version of this receiver, which had no JS runtime available this
 * early and had to open the raw SQLite file by hand — now everything is native, so it can just
 * use the same Room database and repositories as the rest of the app).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val pendingResult = goAsync()
        val container = (context.applicationContext as TymedApplication).container
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val schedules = container.scheduleRepository.listAllEnabledSchedulesWithMedication()
                container.alarmScheduler.rearmAllScheduleAlarms(schedules)
            } catch (error: Exception) {
                // Best-effort recovery — a failure here shouldn't crash the boot sequence.
                // Schedules still self-heal the next time the app is opened
                // (MainActivity's own rearmAllScheduleAlarms backstop call).
            } finally {
                pendingResult.finish()
            }
        }
    }
}
