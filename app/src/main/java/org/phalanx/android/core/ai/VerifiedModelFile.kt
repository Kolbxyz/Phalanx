package org.phalanx.android.core.ai

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext

/** The file is not one of the models this build accepts. It has been deleted. */
class ModelRejectedException : Exception("Not a supported model")

/**
 * One model file the user imported, kept as [fileName] in the app's private storage. It is copied
 * in while its SHA-256 is computed and kept only if that matches one of [known]; a partial or
 * mismatching copy is deleted. Which model it is goes in a small file beside it, so it is not
 * hashed again at every start. Nothing is downloaded: the user fetches the file themselves.
 */
internal class VerifiedModelFile(
    private val context: Context,
    fileName: String,
    private val known: List<KnownModel>
) {
    private val dir = File(context.filesDir, "ai")
    private val file = File(dir, fileName)
    private val partial = File(dir, "$fileName.part")
    private val nameFile = File(dir, "$fileName.name")

    private val mutableInstalled = MutableStateFlow(read())

    /** The name of the imported model, or null if there is none. */
    val installed: StateFlow<String?> = mutableInstalled.asStateFlow()

    fun path(): String? = file.path.takeIf { mutableInstalled.value != null }

    /** Copies [uri] in; [onProgress] gets the fraction done, from 0 to 1. */
    suspend fun import(uri: Uri, onProgress: (Float) -> Unit): Result<String> = withContext(Dispatchers.IO) {
        try {
            dir.mkdirs()
            val model = copyVerified(uri, coroutineContext.job, onProgress)
            partial.renameTo(file)
            nameFile.writeText(model.name)
            mutableInstalled.value = model.name
            Result.success(model.name)
        } catch (e: ModelRejectedException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(e)
        } finally {
            partial.delete()
        }
    }

    suspend fun remove() = withContext(Dispatchers.IO) {
        file.delete()
        nameFile.delete()
        mutableInstalled.value = null
    }

    private fun copyVerified(uri: Uri, job: Job, onProgress: (Float) -> Unit): KnownModel {
        val digest = MessageDigest.getInstance("SHA-256")
        val limit = known.maxOfOrNull { it.maxBytes } ?: throw ModelRejectedException()
        // Progress against the file's own size when the picker tells it, else against the limit.
        val total = sizeOf(uri)?.takeIf { it in 1..limit } ?: limit
        var copied = 0L
        open(uri).use { source ->
            partial.outputStream().use { target ->
                val buffer = ByteArray(BUFFER_BYTES)
                var read = source.read(buffer)
                // No supported model is larger than the limit: stop before filling the storage. The
                // size then matches no model below, so the file is rejected.
                while (read >= 0 && copied <= limit) {
                    job.ensureActive()
                    copied += read
                    digest.update(buffer, 0, read)
                    target.write(buffer, 0, read)
                    onProgress(copied.toFloat() / total)
                    read = source.read(buffer)
                }
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        return known.firstOrNull { copied <= it.maxBytes && it.sha256 == hash } ?: throw ModelRejectedException()
    }

    private fun open(uri: Uri): InputStream =
        context.contentResolver.openInputStream(uri) ?: throw IOException("Unreadable")

    private fun sizeOf(uri: Uri): Long? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            cursor.takeIf { it.moveToFirst() && !it.isNull(0) }?.getLong(0)
        }

    // Only this class writes the file and its name, after checking the hash. A language model
    // imported before names were kept is recognised by its exact size.
    private fun read(): String? {
        if (!file.isFile) return null
        val name = if (nameFile.isFile) nameFile.readText() else null
        return known.firstOrNull { it.name == name || (name == null && it.maxBytes == file.length()) }?.name
    }

    private companion object {
        const val BUFFER_BYTES = 1 shl 20
    }
}
