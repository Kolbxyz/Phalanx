package org.phalanx.android.core.ai.vision

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow
import org.phalanx.android.core.ai.KnownModel
import org.phalanx.android.core.ai.VerifiedModelFile

/**
 * The vision projectors (mmproj) that let the language model read images, published beside it at
 * huggingface.co/mistralai/Ministral-3-3B-Instruct-2512-GGUF. Only these exact files load. Compare
 * a hash with the one on that page before adding one.
 */
internal val KNOWN_VISION_MODELS = listOf<KnownModel>()

/** The vision projector the user imported, one of [KNOWN_VISION_MODELS]; see [VerifiedModelFile]. */
@Singleton
class VisionModelStore @Inject constructor(@ApplicationContext context: Context) {
    private val model = VerifiedModelFile(context, "mmproj.gguf", KNOWN_VISION_MODELS)

    /** The name of the imported projector, or null if there is none. */
    val installed: StateFlow<String?> = model.installed

    internal fun path(): String? = model.path()

    /** Copies [uri] in; [onProgress] gets the fraction done, from 0 to 1. */
    suspend fun import(uri: Uri, onProgress: (Float) -> Unit): Result<String> = model.import(uri, onProgress)

    suspend fun remove() = model.remove()
}
