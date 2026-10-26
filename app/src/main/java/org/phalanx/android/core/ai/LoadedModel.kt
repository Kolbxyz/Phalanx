package org.phalanx.android.core.ai

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.phalanx.android.di.ApplicationScope

/**
 * The model, kept loaded between requests: loading it maps and rearranges about 2 GB of weights,
 * which a summary followed by suggested replies would otherwise pay twice. Released after
 * [IDLE_MILLIS] without a request, when the app leaves the screen, when memory runs low, and
 * when the model is removed or replaced, so it never holds that memory in the background.
 */
@Singleton
internal class LoadedModel @Inject constructor(
    models: ModelStore,
    @ApplicationContext context: Context,
    @ApplicationScope private val scope: CoroutineScope
) {
    private val mutex = Mutex()
    private var handle = 0L
    private var idle: Job? = null

    init {
        scope.launch { models.installed.drop(1).collect { release() } }
        context.registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) scope.launch { release() }
            }

            override fun onConfigurationChanged(newConfig: Configuration) = Unit

            @Deprecated("Superseded by onTrimMemory, still called on older Android versions")
            override fun onLowMemory() {
                scope.launch { release() }
            }
        })
    }

    /** Runs [block] with the model at [path], loading it first if it is not in memory. */
    suspend fun <T> use(path: String, block: suspend (handle: Long) -> T): T = mutex.withLock {
        idle?.cancel()
        if (handle == 0L) {
            handle = withContext(Dispatchers.Default) {
                LlamaNative.load(path, CpuCores.replyThreads, CpuCores.promptThreads)
            }
        }
        try {
            block(handle)
        } finally {
            idle = scope.launch {
                delay(IDLE_MILLIS)
                release()
            }
        }
    }

    private suspend fun release() = mutex.withLock {
        if (handle != 0L) {
            LlamaNative.free(handle)
            handle = 0L
        }
    }

    private companion object {
        const val IDLE_MILLIS = 2 * 60 * 1000L
    }
}
