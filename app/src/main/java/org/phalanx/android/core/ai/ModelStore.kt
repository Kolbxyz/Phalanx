package org.phalanx.android.core.ai

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

/** The language model the user imported, one of [KNOWN_MODELS]; see [VerifiedModelFile]. */
@Singleton
class ModelStore @Inject constructor(@ApplicationContext context: Context) {
    private val model = VerifiedModelFile(context, "model.gguf", KNOWN_MODELS)

    /** The name of the imported model, or null if there is none. */
    val installed: StateFlow<String?> = model.installed

    internal fun path(): String? = model.path()

    /** Copies [uri] in; [onProgress] gets the fraction done, from 0 to 1. */
    suspend fun import(uri: Uri, onProgress: (Float) -> Unit): Result<String> = model.import(uri, onProgress)

    suspend fun remove() = model.remove()
}
