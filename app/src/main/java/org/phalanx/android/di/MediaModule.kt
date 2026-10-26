package org.phalanx.android.di

import android.content.Context
import android.os.Build
import coil3.ImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.memory.MemoryCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.phalanx.android.core.matrix.ActiveClient
import org.phalanx.android.core.matrix.MediaRequestKeyer
import org.phalanx.android.core.matrix.SdkMediaFetcher

@Module
@InstallIn(SingletonComponent::class)
internal object MediaModule {
    private const val MEMORY_SHARE = 0.25

    @Provides
    @Singleton
    fun imageLoader(
        @ApplicationContext context: Context,
        active: ActiveClient,
        @ApplicationScope scope: CoroutineScope
    ): ImageLoader {
        val loader = ImageLoader.Builder(context)
            .components {
                add(SdkMediaFetcher.Factory(active))
                add(MediaRequestKeyer)
                // GIFs, animated WebP and bridges' animated custom emoji play instead of freezing on
                // their first frame: the platform decoder from Android 9, Coil's own before it.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, MEMORY_SHARE).build() }
            // No disk cache on purpose, see SdkMediaFetcher.
            .diskCache(null)
            .build()
        // Decoded images of one account must not be shown to the next one.
        scope.launch { active.syncService.collect { loader.memoryCache?.clear() } }
        return loader
    }
}
