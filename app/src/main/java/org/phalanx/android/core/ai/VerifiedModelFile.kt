package org.phalanx.android.core.ai

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext

/** The file is not one of the models this build accepts. It has been deleted. */
class ModelRejectedException : Exception("Not a supported model")

/**
 * One model file the user downloaded or imported, kept as [fileName] in the app's private storage.
 * It is copied in while its SHA-256 is computed and kept only if that matches one of [known]; a
 * partial or mismatching copy is deleted. Which model it is goes in a small file beside it, so it
 * is not hashed again at every start. Transfers run in [scope], one at a time.
 */
internal class VerifiedModelFile(
    private val context: Context,
    fileName: String,
    private val known: List<KnownModel>,
    scope: CoroutineScope
) {
    private val dir = File(context.filesDir, "ai")
    private val file = File(dir, fileName)
    private val partial = File(dir, "$fileName.part")
    private val nameFile = File(dir, "$fileName.name")
    private val runner = TransferRunner(scope)

    private val mutableInstalled = MutableStateFlow(read())

    /** The name of the installed model, or null if there is none. */
    val installed: StateFlow<String?> = mutableInstalled.asStateFlow()

    /** The download or import under way, or how the last one ended. */
    val transfer: StateFlow<ModelTransfer> = runner.state

    fun path(): String? = file.path.takeIf { mutableInstalled.value != null }

    /** Copies the file at [uri] in, whichever of [known] it is. */
    fun import(uri: Uri) = runner.start { onProgress ->
        install(known) { job -> copyHashed(context, uri, partial, limit(known), job, onProgress) }
    }

    /** Downloads [model] from its publisher. */
    fun download(model: KnownModel) = runner.start { onProgress ->
        install(listOf(model)) { job -> downloadHashed(model.url, partial, model.maxBytes, job, onProgress) }
    }

    fun cancel() = runner.cancel()

    suspend fun remove() = withContext(Dispatchers.IO) {
        file.delete()
        nameFile.delete()
        mutableInstalled.value = null
    }

    // Keeps the copy only if it is one of [accepted]; any other, or a partial one, is deleted.
    private suspend fun install(accepted: List<KnownModel>, copy: (Job) -> HashedCopy) = withContext(Dispatchers.IO) {
        try {
            dir.mkdirs()
            val copied = copy(coroutineContext.job)
            val model = accepted.firstOrNull { copied.size <= it.maxBytes && it.sha256 == copied.sha256 }
                ?: throw ModelRejectedException()
            if (!partial.renameTo(file)) throw IOException("Could not keep the file")
            nameFile.writeText(model.name)
            mutableInstalled.value = model.name
        } finally {
            partial.delete()
        }
    }

    private fun limit(models: List<KnownModel>) = models.maxOfOrNull { it.maxBytes } ?: 0L

    // Only this class writes the file and its name, after checking the hash. A language model
    // imported before names were kept is recognised by its exact size.
    private fun read(): String? {
        if (!file.isFile) return null
        val name = if (nameFile.isFile) nameFile.readText() else null
        return known.firstOrNull { it.name == name || (name == null && it.maxBytes == file.length()) }?.name
    }
}
