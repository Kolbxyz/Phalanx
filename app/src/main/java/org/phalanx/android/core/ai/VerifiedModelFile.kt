package org.phalanx.android.core.ai

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException
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
 * mismatching copy is deleted. Nothing is downloaded: the user fetches the file themselves.
 */
internal class VerifiedModelFile(
    private val context: Context,
    fileName: String,
    private val known: List<KnownModel>
) {
    private val dir = File(context.filesDir, "ai")
    private val file = File(dir, fileName)
    private val partial = File(dir, "$fileName.part")

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
        mutableInstalled.value = null
    }

    private fun copyVerified(uri: Uri, job: Job, onProgress: (Float) -> Unit): KnownModel {
        val digest = MessageDigest.getInstance("SHA-256")
        val limit = known.maxOfOrNull { it.sizeBytes } ?: throw ModelRejectedException()
        var copied = 0L
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Unreadable")
        input.use { source ->
            partial.outputStream().use { target ->
                val buffer = ByteArray(BUFFER_BYTES)
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    job.ensureActive()
                    copied += read
                    // No supported model is larger than this: stop before filling the storage. The
                    // size then matches no model below, so the file is rejected.
                    if (copied > limit) break
                    digest.update(buffer, 0, read)
                    target.write(buffer, 0, read)
                    onProgress(copied.toFloat() / limit)
                }
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        return known.firstOrNull { it.sizeBytes == copied && it.sha256 == hash } ?: throw ModelRejectedException()
    }

    // The size is enough to name a file that only this class could have put there.
    private fun read(): String? = known.firstOrNull { file.isFile && it.sizeBytes == file.length() }?.name

    private companion object {
        const val BUFFER_BYTES = 1 shl 20
    }
}
