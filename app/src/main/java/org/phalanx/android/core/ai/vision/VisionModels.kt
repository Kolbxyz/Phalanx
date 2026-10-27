package org.phalanx.android.core.ai.vision

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import org.phalanx.android.core.ai.KnownModel
import org.phalanx.android.core.ai.MINISTRAL
import org.phalanx.android.core.ai.ModelTransfer
import org.phalanx.android.core.ai.VerifiedModelFile
import org.phalanx.android.di.ApplicationScope

/**
 * The vision projectors (mmproj) that let the language model read images, published beside it at
 * huggingface.co/mistralai/Ministral-3-3B-Instruct-2512-GGUF. Only these exact files load. Compare
 * a hash with the one on that page before adding one.
 */
internal val KNOWN_VISION_MODELS = listOf(
    KnownModel(
        "Ministral-3-3B-Instruct-2512-BF16-mmproj.gguf",
        "$MINISTRAL/Ministral-3-3B-Instruct-2512-BF16-mmproj.gguf",
        841_501_856L,
        "70ca26e41ecea9da997e2887462ad68b848fbf64da011ff4107bbe7353519ee0"
    )
)

/** The vision projector the user imported, one of [KNOWN_VISION_MODELS]; see [VerifiedModelFile]. */
@Singleton
class VisionModelStore @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope
) {
    private val model = VerifiedModelFile(context, "mmproj.gguf", KNOWN_VISION_MODELS, scope)

    /** The name of the imported projector, or null if there is none. */
    val installed: StateFlow<String?> = model.installed

    internal fun path(): String? = model.path()

    /** The download or import under way, or how the last one ended. */
    val transfer: StateFlow<ModelTransfer> = model.transfer

    fun import(uri: Uri) = model.import(uri)

    fun download(known: KnownModel) = model.download(known)

    fun cancel() = model.cancel()

    suspend fun remove() = model.remove()
}
