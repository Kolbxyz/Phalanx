package org.phalanx.android.core.ai.speech

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow
import org.phalanx.android.core.ai.KnownModel
import org.phalanx.android.core.ai.VerifiedModelFile

/**
 * The multilingual Whisper models (MIT), as whisper.cpp publishes them at
 * huggingface.co/ggerganov/whisper.cpp: "small" is quick, "large-v3-turbo" the most accurate.
 * Only these exact files load. Compare a hash with the one on that page before adding a model.
 */
internal val KNOWN_SPEECH_MODELS = listOf<KnownModel>()

/** The speech model the user imported, one of [KNOWN_SPEECH_MODELS]; see [VerifiedModelFile]. */
@Singleton
class SpeechModelStore @Inject constructor(@ApplicationContext context: Context) {
    private val model = VerifiedModelFile(context, "speech.bin", KNOWN_SPEECH_MODELS)

    /** The name of the imported model, or null if there is none. */
    val installed: StateFlow<String?> = model.installed

    internal fun path(): String? = model.path()

    /** Copies [uri] in; [onProgress] gets the fraction done, from 0 to 1. */
    suspend fun import(uri: Uri, onProgress: (Float) -> Unit): Result<String> = model.import(uri, onProgress)

    suspend fun remove() = model.remove()
}
