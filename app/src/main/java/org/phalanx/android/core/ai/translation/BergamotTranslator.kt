package org.phalanx.android.core.ai.translation

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.phalanx.android.core.ai.CpuCores
import org.phalanx.android.di.ApplicationScope

/**
 * Translates with Bergamot, one loaded pair at a time: about 50 MB, loaded in well under a second,
 * so it is released after [IDLE_MILLIS] rather than kept around like the language model.
 */
@Singleton
class BergamotTranslator @Inject internal constructor(
    private val store: TranslationModelStore,
    @ApplicationScope private val scope: CoroutineScope
) {
    private val lock = Mutex()
    private var loaded: Pair<LanguagePair, Long>? = null
    private var idle: Job? = null

    /** [text] translated by [pair]'s model, which must be installed. */
    suspend fun translate(pair: LanguagePair, text: String): Result<String> = lock.withLock {
        idle?.cancel()
        try {
            withContext(Dispatchers.Default) {
                val handle = handleFor(pair)
                Result.success(BergamotNative.translate(handle, text.toByteArray()).decodeToString())
            }
        } catch (e: IllegalStateException) {
            Result.failure(e)
        } finally {
            idle = scope.launch {
                delay(IDLE_MILLIS)
                lock.withLock { release() }
            }
        }
    }

    private fun handleFor(pair: LanguagePair): Long {
        loaded?.takeIf { it.first == pair }?.let { return it.second }
        release()
        val files = checkNotNull(store.filesOf(pair)) { "Pair not installed" }
        val handle = BergamotNative.load(
            files.model, files.shortlist, files.sourceVocab, files.targetVocab, CpuCores.promptThreads
        )
        loaded = pair to handle
        return handle
    }

    private fun release() {
        loaded?.let { BergamotNative.free(it.second) }
        loaded = null
    }

    private companion object {
        const val IDLE_MILLIS = 60_000L
    }
}
