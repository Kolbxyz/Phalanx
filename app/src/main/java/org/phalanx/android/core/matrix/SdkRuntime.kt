package org.phalanx.android.core.matrix

import android.content.Context
import org.matrix.rustcomponents.sdk.LogLevel
import org.matrix.rustcomponents.sdk.TracingConfiguration
import org.matrix.rustcomponents.sdk.initPlatform
import org.phalanx.android.BuildConfig

/** One-time SDK process setup, kept here so no other package imports SDK types. */
object SdkRuntime {
    fun init(context: Context) {
        // Must run before any SDK call: without it every HTTPS request fails with "Expect
        // rustls-platform-verifier to be initialized".
        org.matrix.rustsdk.Android.init(context)
        // SDK logs go to logcat in debug builds only, and never to files: they can name rooms and users.
        initPlatform(
            config = TracingConfiguration(
                logLevel = if (BuildConfig.DEBUG) LogLevel.INFO else LogLevel.WARN,
                traceLogPacks = emptyList(),
                extraTargets = emptyList(),
                writeToStdoutOrSystem = BuildConfig.DEBUG,
                writeToFiles = null,
                sentryConfig = null
            ),
            useLightweightTokioRuntime = false
        )
    }
}
