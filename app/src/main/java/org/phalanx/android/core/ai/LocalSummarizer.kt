package org.phalanx.android.core.ai

import java.io.ByteArrayOutputStream
import java.time.ZonedDateTime
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.phalanx.android.core.ai.actions.MessageAction
import org.phalanx.android.core.ai.actions.actionsOf
import org.phalanx.android.core.ai.actions.actionsPrompt
import org.phalanx.android.core.matrix.ActiveClient
import org.phalanx.android.di.ApplicationScope

/** No model has been imported. */
class ModelMissingException : Exception("No model")

/** The phone has no 64-bit processor, so the native library is not there. */
class SummariesUnsupportedException : Exception("Unsupported device")

/**
 * Runs the imported model on this phone's CPU: summaries, reply suggestions and translations. The
 * model stays loaded for a short while after a request (see [LoadedModel]). One request at a time.
 */
@Singleton
class LocalSummarizer @Inject internal constructor(
    private val models: ModelStore,
    private val loaded: LoadedModel,
    active: ActiveClient,
    @ApplicationScope scope: CoroutineScope
) {
    private val lock = Mutex()

    // The last few replies by model and exact prompt, so asking again with nothing new runs no
    // inference. Kept in memory only, under [lock]: a reply is room content and never hits disk.
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

    /** Three short replies the user, named [you] in [lines], could send next. */
    suspend fun suggestReplies(lines: List<SummaryLine>, you: String): Result<List<String>> =
        complete(repliesPrompt(SummaryPrompt.of(lines), you), REPLIES_TOKENS) {}.map { repliesOf(it, you) }

    /**
     * [text] in [language], an English name such as "French", as it is written. Only the first
     * [MAX_TRANSLATED_CHARS] are translated.
     */
    suspend fun translate(text: String, language: String, onText: (String) -> Unit): Result<String> =
        complete(translatePrompt(text, language), TRANSLATION_TOKENS, onText)

    /**
     * What the user, named [you] in [lines], missed in one room, in up to three points, with what
     * waits on them and what they promised. Fails when the reply is not the shape asked for.
     */
    suspend fun catchUp(lines: List<SummaryLine>, you: String): Result<CatchUp> =
        complete(catchUpPrompt(SummaryPrompt.of(lines), you), CATCH_UP_TOKENS) {}.mapCatching {
            catchUpOf(it) ?: throw IllegalStateException("Not a catch-up")
        }

    /**
     * What [message] plans or asks that another app could take over: events, alarms, timers,
     * reminders, tasks, places, numbers and tracking codes. [before] is context only; relative dates
     * are read from [sentAt].
     */
    suspend fun findActions(
        message: SummaryLine,
        before: List<SummaryLine>,
        sentAt: ZonedDateTime
    ): Result<List<MessageAction>> =
        complete(actionsPrompt(message, before, sentAt), ACTIONS_TOKENS) {}.map(::actionsOf)

    /** One long message in a few points, as it is written. */
    suspend fun summarizeMessage(text: String, onText: (String) -> Unit): Result<String> =
        complete(messageSummaryPrompt(text), SUMMARY_TOKENS, onText)

    private suspend fun complete(prompt: Prompt, maxTokens: Int, onText: (String) -> Unit): Result<String> {
        val path = models.path()
        return when {
            !LlamaNative.isAvailable -> Result.failure(SummariesUnsupportedException())
            path == null -> Result.failure(ModelMissingException())
            else -> lock.withLock {
                val key = listOf(path, prompt.system, prompt.user, prompt.grammar.orEmpty())
                    .joinToString("\u0000")
                cache[key]?.let { return@withLock Result.success(it) }
                withContext(Dispatchers.Default) {
                    try {
                        Result.success(run(path, prompt, maxTokens, onText))
                    } catch (e: IllegalStateException) {
                        Result.failure(e)
                    }
                }.onSuccess { cache[key] = it }
            }
        }
    }

    private suspend fun run(path: String, prompt: Prompt, maxTokens: Int, onText: (String) -> Unit): String =
        loaded.use(path) { handle -> generate(handle, prompt, maxTokens, onText) }

    private suspend fun generate(handle: Long, prompt: Prompt, maxTokens: Int, onText: (String) -> Unit): String =
        coroutineScope {
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
            } finally {
                finished.set(true)
                // Joined before the handle can be freed, so a late cancel never touches freed memory.
                withContext(NonCancellable) { watcher.cancelAndJoin() }
            }
        }

    private companion object {
        // Room for SummaryPrompt's budget plus the reply; Ministral 3 takes far more, but the KV
        // cache grows with it and a phone has little memory to spare.
        const val CONTEXT_TOKENS = 6144
        const val SUMMARY_TOKENS = 400
        const val REPLIES_TOKENS = 120
        const val TRANSLATION_TOKENS = 1_600

        // Three short points and two lines.
        const val CATCH_UP_TOKENS = 250

        // Four items of about eighty tokens each, with room to close the array.
        const val ACTIONS_TOKENS = 400
        const val CACHE_SIZE = 8
        const val LOAD_FACTOR = 0.75f
    }
}
