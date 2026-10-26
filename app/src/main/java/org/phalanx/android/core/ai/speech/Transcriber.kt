package org.phalanx.android.core.ai.speech

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.phalanx.android.core.ai.AiDisk
import org.phalanx.android.core.ai.CpuCores
import org.phalanx.android.core.ai.LoadedNative
import org.phalanx.android.core.matrix.ActiveClient
import org.phalanx.android.di.ApplicationScope

/** No speech model has been imported. */
class SpeechModelMissingException : Exception("No speech model")

/** The phone has no 64-bit processor, so the native library is not there. */
class SpeechUnsupportedException : Exception("Unsupported device")

/** A voice message as text, in [language] (a code such as "fr"); [partial] when only its start was read. */
@Serializable
data class Transcript(val text: String, val language: String, val partial: Boolean)

/**
 * Writes voice messages out as text with the imported Whisper model, on this phone only. Any
 * language Whisper knows is detected by itself. One message at a time; the model stays loaded for
 * a short while after (see [LoadedNative]).
 */
@Singleton
class Transcriber @Inject internal constructor(
    private val models: SpeechModelStore,
    @ApplicationContext private val context: Context,
    @ApplicationScope scope: CoroutineScope,
    active: ActiveClient,
    private val disk: AiDisk
) {
    private val loaded = LoadedNative(
        models.installed,
        context,
        scope,
        load = { WhisperNative.load(it, CpuCores.promptThreads) },
        free = WhisperNative::free
    )
    private val lock = Mutex()

    // The last transcripts by message, so showing one again runs nothing. Under [lock]. On disk
    // too, sealed, only while the user keeps AI results (see [AiDisk]).
    private val cache = object : LinkedHashMap<String, Transcript>(CACHE_SIZE, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Transcript>?) = size > CACHE_SIZE
    }

    init {
        // A new sync service means sign-in or sign-out: the transcripts belong to another account.
        scope.launch { active.syncService.collect { lock.withLock { cache.clear() } } }
    }

    /** The transcript of [key] if it was already written, without running anything. */
    suspend fun cached(key: String): Transcript? = lock.withLock { cachedLocked(key) }

    private suspend fun cachedLocked(key: String): Transcript? =
        cache[key] ?: disk.read(disk.transcripts, key)?.also { cache[key] = it }

    /**
     * Writes out the audio at [audio], known as [key] (its event, say). Calls [onText] with the text
     * so far as it grows.
     */
    suspend fun transcribe(key: String, audio: Uri, onText: (String) -> Unit): Result<Transcript> {
        val path = models.path()
        return when {
            !WhisperNative.isAvailable -> Result.failure(SpeechUnsupportedException())
            path == null -> Result.failure(SpeechModelMissingException())
            else -> lock.withLock {
                cachedLocked(key)?.let { return@withLock Result.success(it) }
                withContext(Dispatchers.Default) {
                    try {
                        val decoded = decodeAudio(context, audio)
                        Result.success(loaded.use(path) { run(it, decoded, onText) })
                    } catch (e: UndecodableAudioException) {
                        Result.failure(e)
                    } catch (e: IllegalStateException) {
                        Result.failure(e)
                    }
                }.onSuccess {
                    cache[key] = it
                    disk.keep(disk.transcripts, key, it)
                }
            }
        }
    }

    private suspend fun run(handle: Long, audio: DecodedAudio, onText: (String) -> Unit): Transcript =
        coroutineScope {
            val finished = AtomicBoolean(false)
            // Cancelling the coroutine stops the native loop, which no suspension point can interrupt.
            val watcher = launch {
                try {
                    awaitCancellation()
                } finally {
                    if (!finished.get()) WhisperNative.cancel(handle)
                }
            }
            try {
                val text = ByteArrayOutputStream()
                val language = WhisperNative.transcribe(handle, audio.samples, AUTO) { segment ->
                    text.write(segment)
                    onText(text.toString(Charsets.UTF_8.name()).trim())
                }
                ensureActive()
                Transcript(text.toString(Charsets.UTF_8.name()).trim(), language ?: AUTO, audio.cut)
            } finally {
                finished.set(true)
                // Joined before the handle can be freed, so a late cancel never touches freed memory.
                withContext(NonCancellable) { watcher.cancelAndJoin() }
            }
        }

    private companion object {
        const val AUTO = "auto"
        const val CACHE_SIZE = 32
        const val LOAD_FACTOR = 0.75f
    }
}
