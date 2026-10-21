package org.phalanx.android

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import org.matrix.rustcomponents.sdk.LogLevel
import org.matrix.rustcomponents.sdk.TracingConfiguration
import org.matrix.rustcomponents.sdk.initPlatform
import org.phalanx.android.core.push.PushRegistrar
import javax.inject.Inject

@HiltAndroidApp
class PhalanxApplication : Application(), SingletonImageLoader.Factory {
    @Inject
    lateinit var imageLoader: Lazy<ImageLoader>

    @Inject
    internal lateinit var pushRegistrar: PushRegistrar

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader.get()

    override fun onCreate() {
        super.onCreate()
        // Must run before any SDK call: without it every HTTPS request fails with "Expect
        // rustls-platform-verifier to be initialized".
        org.matrix.rustsdk.Android.init(this)
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
        pushRegistrar.start()
    }
}
