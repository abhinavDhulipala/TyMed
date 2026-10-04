package com.tymed.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tymed.app.alarm.AlarmActivity
import com.tymed.app.alarm.AlarmReceiver
import com.tymed.app.alarm.RingingAlarmInfo
import com.tymed.app.alarm.RingingAlarmTracker
import com.tymed.app.ui.TymedApp
import com.tymed.app.ui.theme.TymedTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as TymedApplication
        setContent {
            TymedTheme {
                TymedApp(container = app.container)
            }
        }

        // If a dose alert is still firing (e.g. the ring service/fallback notification is up)
        // and the app gets opened some other way than tapping that notification — the launcher
        // icon, recents — show the alert screen anyway rather than leaving it only reachable by
        // re-finding the notification. Collecting (rather than a one-off check) also catches an
        // alarm that starts firing while this screen is already in the foreground.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                RingingAlarmTracker.current.collect { info ->
                    if (info != null) showAlarmActivity(info)
                }
            }
        }
    }

    private fun showAlarmActivity(info: RingingAlarmInfo) {
        val intent = Intent(this, AlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(AlarmReceiver.EXTRA_REQUEST_CODE, info.requestCode)
            putExtra(AlarmReceiver.EXTRA_SCHEDULE_ID, info.scheduleId)
            putExtra(AlarmReceiver.EXTRA_MEDICATION_ID, info.medicationId)
            putExtra(AlarmReceiver.EXTRA_MEDICATION_NAME, info.medicationName)
            putExtra(AlarmReceiver.EXTRA_DOSAGE, info.dosage)
            putExtra(AlarmReceiver.EXTRA_RINGING_SINCE_MILLIS, info.ringingSinceMillis)
        }
        startActivity(intent)
    }
}
