package org.phalanx.android.core.ai

import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.phalanx.android.core.matrix.ActiveClient
import org.phalanx.android.core.prefs.SettingsStore
import org.phalanx.android.di.ApplicationScope

/** No model has been imported. */
class ModelMissingException : Exception("No model")

/** The phone has no 64-bit processor, so the native library is not there. */
class SummariesUnsupportedException : Exception("Unsupported device")

/**
 * Writes summaries, catch-ups and translations: on this phone's CPU with the imported model, which
 * stays loaded for a short while after a request (see [LoadedModel]), or, when the user turned
 * cloud writing on, by Claude with their own key (see [ClaudeApi]). One request at a time.
 */
@Singleton
class LocalSummarizer @Inject internal constructor(
    private val models: ModelStore,
    private val loaded: LoadedModel,
    private val settings: SettingsStore,
    private val cloudKey: CloudKey,
    private val claude: ClaudeApi,
    active: ActiveClient,
    @ApplicationScope scope: CoroutineScope
) {
    private val lock = Mutex()

    /** True while Claude writes in place of the model on this phone: what is asked for is sent out. */
    val inCloud: Flow<Boolean> = settings.settings.map { it.aiCloud }.distinctUntilChanged()

    // The last few replies by model and exact prompt, so asking again with nothing new runs no
    // inference and sends nothing out. Kept in memory only, under [lock]: a reply is room content
    // and never hits disk.
    private val cache = object : LinkedHashMap<String, String>(CACHE_SIZE, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > CACHE_SIZE
    }

    init {
        // A new sync service means sign-in or sign-out: the summaries belong to another account.
        scope.launch { active.syncService.collect { lock.withLock { cache.clear() } } }
    }

    /**
     * Sums [lines] up as points citing their messages. Calls [onText] with the reply so far, as it
     * grows; a cached reply comes back at once.
     */
    suspend fun summarize(lines: List<SummaryLine>, onText: (String) -> Unit): Result<Summary> {
        val prompt = SummaryPrompt.of(lines)
        return complete(prompt, SUMMARY_TOKENS) { onText(withoutCitations(it)) }
            .map { Summary(pointsOf(it, prompt.eventIds), prompt.count) }
    }

    /**
     * [text] in [language], an English name such as "French", as it is written. Only the first
     * [MAX_TRANSLATED_CHARS] are translated.
     */
    suspend fun translate(text: String, language: String, onText: (String) -> Unit): Result<String> =
        complete(translatePrompt(text, language), TRANSLATION_TOKENS, onText)

    /**
     * What the user missed in one room, in up to three points, with what waits on them and what
     * they promised. Their own [lines] are signed [you]. Fails when the reply is not the shape
     * asked for.
     */
    suspend fun catchUp(lines: List<CatchUpLine>, you: String, isDirect: Boolean): Result<CatchUp> {
        val prompt = CatchUpPrompt.of(lines, you, isDirect)
        return complete(prompt, CATCH_UP_TOKENS) {}.mapCatching {
            prompt.read(it) ?: throw IllegalStateException("Not a catch-up")
        }
    }

    /** One long message in a few points, as it is written. */
    suspend fun summarizeMessage(text: String, onText: (String) -> Unit): Result<String> =
        complete(messageSummaryPrompt(text), SUMMARY_TOKENS, onText)

    private suspend fun complete(prompt: Prompt, maxTokens: Int, onText: (String) -> Unit): Result<String> =
        if (settings.settings.first().aiCloud) byClaude(prompt, onText) else onPhone(prompt, maxTokens, onText)

    private suspend fun onPhone(prompt: Prompt, maxTokens: Int, onText: (String) -> Unit): Result<String> {
        val path = models.path()
        return when {
            !LlamaNative.isAvailable -> Result.failure(SummariesUnsupportedException())
            path == null -> Result.failure(ModelMissingException())
            else -> cached(path, prompt) {
                withContext(Dispatchers.Default) {
                    try {
                        Result.success(run(path, prompt, maxTokens, onText))
                    } catch (e: IllegalStateException) {
                        Result.failure(e)
                    }
                }
            }
        }
    }

    private suspend fun byClaude(prompt: Prompt, onText: (String) -> Unit): Result<String> {
        val key = cloudKey.read() ?: return Result.failure(CloudKeyMissingException())
        return cached(CLAUDE_MODEL, prompt) {
            try {
                Result.success(claude.complete(key, prompt, onText))
            } catch (e: CloudKeyRefusedException) {
                Result.failure(e)
            } catch (e: CloudFailedException) {
                Result.failure(e)
            } catch (e: IOException) {
                Result.failure(e)
            }
        }
    }

    // [model] is part of what a reply is kept under: the phone's and Claude's are not the same.
    private suspend fun cached(model: String, prompt: Prompt, write: suspend () -> Result<String>): Result<String> =
        lock.withLock {
            val key = listOf(model, prompt.system, prompt.user, prompt.grammar.orEmpty()).joinToString("\u0000")
            cache[key]?.let { return@withLock Result.success(it) }
            write().onSuccess { cache[key] = it }
        }

    private suspend fun run(path: String, prompt: Prompt, maxTokens: Int, onText: (String) -> Unit): String =
        loaded.use(path) { handle -> generate(handle, prompt, maxTokens, onText) }

    // Cancelling the coroutine stops the native loop, which no suspension point can interrupt.
    private suspend fun generate(handle: Long, prompt: Prompt, maxTokens: Int, onText: (String) -> Unit): String =
        cancellableNative({ LlamaNative.cancel(handle) }) {
            val reply = ByteArrayOutputStream()
            LlamaNative.complete(
                handle,
                prompt.system.toByteArray(),
                prompt.user.toByteArray(),
                CONTEXT_TOKENS,
                maxTokens,
                prompt.grammar?.toByteArray()
            ) { piece ->
                reply.write(piece)
                onText(reply.toString(Charsets.UTF_8.name()))
                isActive
            }
            ensureActive()
            reply.toString(Charsets.UTF_8.name()).trim()
        }

    private companion object {
        const val SUMMARY_TOKENS = 400
        const val TRANSLATION_TOKENS = 1_600

        // Three short points and two lines.
        const val CATCH_UP_TOKENS = 320

        const val CACHE_SIZE = 8
        const val LOAD_FACTOR = 0.75f
    }
}
