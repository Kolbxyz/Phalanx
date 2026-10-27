package org.phalanx.android.core.ai

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import org.phalanx.android.di.ApplicationScope

/** The language model the user imported, one of [KNOWN_MODELS]; see [VerifiedModelFile]. */
@Singleton
class ModelStore @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope
) {
    private val model = VerifiedModelFile(context, "model.gguf", KNOWN_MODELS, scope)

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
