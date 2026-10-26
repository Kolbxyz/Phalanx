package org.phalanx.android.core.ai.vision

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.phalanx.android.core.ai.CONTEXT_TOKENS
import org.phalanx.android.core.ai.LlamaNative
import org.phalanx.android.core.ai.LoadedModel
import org.phalanx.android.core.ai.ModelMissingException
import org.phalanx.android.core.ai.ModelStore
import org.phalanx.android.core.ai.cancellableNative

/** No vision projector has been imported. */
class VisionModelMissingException : Exception("No vision model")

/** What a photo shows, in a sentence or two, and the [text] written in it, word for word. */
@Serializable
data class PhotoWords(val description: String, val text: String)

/**
 * Describes photos with the language model and its vision projector, on this phone only. Shares
 * the loaded model with summaries (see [LoadedModel]), so one request runs at a time.
 */
@Singleton
class PhotoDescriber @Inject internal constructor(
    private val models: ModelStore,
    private val vision: VisionModelStore,
    private val loaded: LoadedModel,
    @ApplicationContext private val context: Context
) {
    /** Whether both models are there and the phone runs them. */
    val ready: Flow<Boolean> = combine(models.installed, vision.installed) { model, projector ->
        LlamaNative.isAvailable && model != null && projector != null
    }

    suspend fun describe(image: Uri): Result<PhotoWords> {
        val path = models.path()
        val projector = vision.path()
        return when {
            path == null -> Result.failure(ModelMissingException())
            projector == null -> Result.failure(VisionModelMissingException())
            else -> withContext(Dispatchers.Default) {
                try {
                    val pixels = pixelsOf(context, image)
                    val reply = loaded.use(path) { handle -> run(handle, projector, pixels) }
                    wordsOf(reply)?.let { Result.success(it) }
                        ?: Result.failure(IllegalStateException("Not a description"))
                } catch (e: UndecodableImageException) {
                    Result.failure(e)
                } catch (e: IllegalStateException) {
                    Result.failure(e)
                }
            }
        }
    }

    private suspend fun run(handle: Long, projector: String, pixels: ImagePixels): String =
        cancellableNative({ LlamaNative.cancel(handle) }) {
            val reply = ByteArrayOutputStream()
            LlamaNative.describe(
                handle, projector, pixels.rgb, pixels.width, pixels.height,
                SYSTEM.toByteArray(), userPrompt().toByteArray(), CONTEXT_TOKENS, MAX_TOKENS,
                WORDS_GRAMMAR.toByteArray()
            ) { piece ->
                reply.write(piece)
                isActive
            }
            ensureActive()
            reply.toString(Charsets.UTF_8.name()).trim()
        }

    private fun userPrompt(): String {
        val language = Locale.getDefault().getDisplayLanguage(Locale.ENGLISH).ifEmpty { "English" }
        return "Reply with JSON only. description: what this photo shows, in one or two sentences " +
            "in $language, naming objects, places, people's activities and any brand or document type. " +
            "text: every piece of text visible in it, word for word, else an empty string."
    }

    private companion object {
        const val SYSTEM = "You describe photos so they can be found by search later. Describe only what is visible."

        // Two sentences and the text of a document or screenshot.
        const val MAX_TOKENS = 300
    }
}

private val wordsJson = Json { ignoreUnknownKeys = true }

/** The model's reply as [PhotoWords]; null when it is not the JSON asked for, cut off say. */
internal fun wordsOf(reply: String): PhotoWords? = try {
    wordsJson.decodeFromString<PhotoWords>(reply)
} catch (_: SerializationException) {
    null
} catch (_: IllegalArgumentException) {
    null
}

private val WORDS_GRAMMAR = """
root ::= "{" ws "\"description\":" ws text ws "," ws "\"text\":" ws text ws "}"
text ::= "\"" char{0,600} "\""
char ::= [^"\\\x00-\x1F] | "\\" ["\\/nt]
ws ::= [ \n]{0,2}
""".trimIndent()
