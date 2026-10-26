package org.phalanx.android.core.ai

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * A native model, kept loaded between requests: loading maps and rearranges hundreds of megabytes
 * of weights, which back-to-back requests would otherwise pay each time. Released after
 * [IDLE_MILLIS] without a request, when the app leaves the screen, when memory runs low, and when
 * [installed] changes, so it never holds that memory in the background.
 */
internal open class LoadedNative(
    installed: Flow<String?>,
    context: Context,
    private val scope: CoroutineScope,
    private val load: (path: String) -> Long,
    private val free: (handle: Long) -> Unit
) {
    private val mutex = Mutex()
    private var handle = 0L
    private var idle: Job? = null

    init {
        scope.launch { installed.drop(1).collect { release() } }
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
        if (handle == 0L) handle = withContext(Dispatchers.Default) { load(path) }
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
            free(handle)
            handle = 0L
        }
    }

    private companion object {
        const val IDLE_MILLIS = 2 * 60 * 1000L
    }
}
