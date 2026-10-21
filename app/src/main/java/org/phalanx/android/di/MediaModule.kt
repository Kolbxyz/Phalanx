package org.phalanx.android.di

import android.content.Context
import coil3.ImageLoader
import coil3.memory.MemoryCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.phalanx.android.core.matrix.ActiveClient
import org.phalanx.android.core.matrix.MediaRequestKeyer
import org.phalanx.android.core.matrix.SdkMediaFetcher
import javax.inject.Singleton

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
