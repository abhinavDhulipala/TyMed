package com.tymed.app

import android.app.Application
import com.tymed.app.observability.initSentry

class TymedApplication : Application() {

    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        initSentry(this)
    }
}
