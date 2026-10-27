package org.phalanx.android.core.ai.speech

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import org.phalanx.android.core.ai.KnownModel
import org.phalanx.android.core.ai.ModelTransfer
import org.phalanx.android.core.ai.VerifiedModelFile
import org.phalanx.android.di.ApplicationScope

/**
 * The multilingual Whisper models (MIT), as whisper.cpp publishes them at
 * huggingface.co/ggerganov/whisper.cpp: "base" is light and quick, "small" a balance,
 * "large-v3-turbo" the most accurate and the heaviest. Only these exact files load. Compare a
 * hash with the one on that page before adding a model.
 */
internal val KNOWN_SPEECH_MODELS = listOf(
    KnownModel(
        "ggml-base-q5_1.bin",
        "$WHISPER/ggml-base-q5_1.bin",
        59_707_625L,
        "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898"
    ),
    KnownModel(
        "ggml-small-q5_1.bin",
        "$WHISPER/ggml-small-q5_1.bin",
        190_085_487L,
        "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb"
    ),
    KnownModel(
        "ggml-large-v3-turbo-q5_0.bin",
        "$WHISPER/ggml-large-v3-turbo-q5_0.bin",
        574_041_195L,
        "394221709cd5ad1f40c46e6031ca61bce88931e6e088c188294c6d5a55ffa7e2"
    )
)

private const val WHISPER = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main"

/** The speech model the user imported, one of [KNOWN_SPEECH_MODELS]; see [VerifiedModelFile]. */
@Singleton
class SpeechModelStore @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope
) {
    private val model = VerifiedModelFile(context, "speech.bin", KNOWN_SPEECH_MODELS, scope)

    /** The name of the imported model, or null if there is none. */
    val installed: StateFlow<String?> = model.installed

    internal fun path(): String? = model.path()

    /** The download or import under way, or how the last one ended. */
    val transfer: StateFlow<ModelTransfer> = model.transfer

    fun import(uri: Uri) = model.import(uri)

    fun download(known: KnownModel) = model.download(known)

    fun cancel() = model.cancel()

    suspend fun remove() = model.remove()
}
