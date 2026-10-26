package org.phalanx.android

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import org.phalanx.android.core.matrix.MessageArchiver
import org.phalanx.android.core.matrix.SdkRuntime
import org.phalanx.android.core.push.PushRegistrar

@HiltAndroidApp
class PhalanxApplication : Application(), SingletonImageLoader.Factory {
    @Inject
    lateinit var imageLoader: Lazy<ImageLoader>

    @Inject
    internal lateinit var pushRegistrar: PushRegistrar

    @Inject
    internal lateinit var messageArchiver: MessageArchiver

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader.get()

    override fun onCreate() {
        super.onCreate()
        SdkRuntime.init(this)
        pushRegistrar.start()
        messageArchiver.start()
    }
}
