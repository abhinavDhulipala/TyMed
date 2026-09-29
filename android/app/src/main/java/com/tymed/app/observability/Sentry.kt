package com.tymed.app.observability

import android.content.Context
import io.sentry.android.core.SentryAndroid
import io.sentry.Sentry

/** No-ops unless a DSN is configured (BuildConfig field, wired from a Gradle property /
 * environment variable — see android/app/build.gradle) — medication names/doses/schedules never
 * leave the device; crash reports are diagnostic only (no PII, no performance tracing). */
fun initSentry(context: Context) {
    val dsn = com.tymed.app.BuildConfig.SENTRY_DSN
    if (dsn.isBlank()) return
    SentryAndroid.init(context) { options ->
        options.dsn = dsn
        options.isSendDefaultPii = false
        options.tracesSampleRate = 0.0
    }
}

/** App-wide "report a caught, non-fatal error" call — never throws itself. */
fun captureException(error: Throwable) {
    try {
        Sentry.captureException(error)
    } catch (ignored: Exception) {
        // Sentry not initialized (no DSN) — nothing to report to.
    }
}
