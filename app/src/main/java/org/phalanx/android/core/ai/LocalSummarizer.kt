package org.phalanx.android.core.ai

import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** No model has been imported. */
class ModelMissingException : Exception("No model")

/** The phone has no 64-bit processor, so the native library is not there. */
class SummariesUnsupportedException : Exception("Unsupported device")

/**
 * Sums up a conversation with the imported model, on this phone's CPU. The model is loaded for
 * each summary and released after, so it holds no memory between uses. One summary at a time.
 */
@Singleton
class LocalSummarizer @Inject constructor(private val models: ModelStore) {
    private val lock = Mutex()

    // The last few replies by model and exact prompt, so asking again with nothing new runs no
    // inference. Kept in memory only, under [lock]: a summary is room content and never hits disk.
    private val cache = object : LinkedHashMap<String, String>(CACHE_SIZE, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > CACHE_SIZE
    }

    /** Calls [onText] with the whole reply so far, as it grows; a cached reply comes back at once. */
    suspend fun summarize(lines: List<SummaryLine>, onText: (String) -> Unit): Result<String> {
        val path = models.path()
        return when {
            !LlamaNative.isAvailable -> Result.failure(SummariesUnsupportedException())
            path == null -> Result.failure(ModelMissingException())
            else -> lock.withLock {
                val prompt = SummaryPrompt.of(lines)
                val key = listOf(path, prompt.system, prompt.user).joinToString("\u0000")
                cache[key]?.let { return@withLock Result.success(it) }
                withContext(Dispatchers.Default) {
                    try {
                        Result.success(run(path, prompt, onText))
                    } catch (e: IllegalStateException) {
                        Result.failure(e)
                    }
                }.onSuccess { cache[key] = it }
            }
        }
    }

    private suspend fun run(path: String, prompt: SummaryPrompt, onText: (String) -> Unit): String = coroutineScope {
        val handle = LlamaNative.load(path, THREADS)
        val finished = AtomicBoolean(false)
        // Cancelling the coroutine stops the native loop, which no suspension point can interrupt.
        val watcher = launch {
            try {
                awaitCancellation()
            } finally {
                if (!finished.get()) LlamaNative.cancel(handle)
            }
        }
        try {
            val reply = ByteArrayOutputStream()
            LlamaNative.complete(
                handle, prompt.system.toByteArray(), prompt.user.toByteArray(), CONTEXT_TOKENS, MAX_NEW_TOKENS
            ) { piece ->
                reply.write(piece)
                onText(reply.toString(Charsets.UTF_8.name()))
                isActive
            }
            ensureActive()
            reply.toString(Charsets.UTF_8.name()).trim()
        } finally {
            finished.set(true)
            // Joined before the handle is freed, so a late cancel never touches freed memory.
            withContext(NonCancellable) { watcher.cancelAndJoin() }
            LlamaNative.free(handle)
        }
    }

    private companion object {
        // Room for SummaryPrompt's budget plus the reply; Ministral 3 takes far more, but the KV
        // cache grows with it and a phone has little memory to spare.
        const val CONTEXT_TOKENS = 6144
        const val MAX_NEW_TOKENS = 400
        const val CACHE_SIZE = 8
        const val LOAD_FACTOR = 0.75f

        // The big cores of an 8-core phone; more threads fight over memory bandwidth for no gain.
        val THREADS = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 4)
    }
}
