package com.tymed.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.tymed.app.ui.TymedApp
import com.tymed.app.ui.theme.TymedTheme

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
    }
}
