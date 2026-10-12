package com.tymed.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tymed.app.alarm.RingingAlarmInfo
import com.tymed.app.alarm.RingingAlarmTracker
import com.tymed.app.alarm.ringingAlarmIntent
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
        // alarm that starts firing while this screen is already in the foreground. If more than
        // one alarm is ringing at once, AlarmActivity itself chains through the rest once the
        // first is resolved — this only needs to kick off the first one.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                RingingAlarmTracker.current.collect { infos ->
                    infos.firstOrNull()?.let { showAlarmActivity(it) }
                }
            }
        }
    }

    private fun showAlarmActivity(info: RingingAlarmInfo) {
        startActivity(ringingAlarmIntent(this, info))
    }
}
