package org.phalanx.android.core.ai

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive

/** A file copied in: the SHA-256 of what was copied, in hex, and how many bytes that was. */
internal class HashedCopy(val sha256: String, val size: Long)

/**
 * Copies [uri] into [target] while hashing it, stopping once past [limit] bytes: no accepted file
 * is larger, so a wrong one never fills the storage, and its size then matches nothing. [onProgress]
 * gets the fraction done, against the file's own size when the picker tells it.
 */
internal fun copyHashed(
    context: Context,
    uri: Uri,
    target: File,
    limit: Long,
    job: Job,
    onProgress: (Float) -> Unit
): HashedCopy = open(context, uri).use { source ->
    copyHashed(source, sizeOf(context, uri), target, limit, job, onProgress)
}

/** As above, from [source], whose size is [size] when known. */
internal fun copyHashed(
    source: InputStream,
    size: Long?,
    target: File,
    limit: Long,
    job: Job,
    onProgress: (Float) -> Unit
): HashedCopy {
    val digest = MessageDigest.getInstance("SHA-256")
    val total = size?.takeIf { it in 1..limit } ?: limit
    var copied = 0L
    target.outputStream().use { output ->
        val buffer = ByteArray(BUFFER_BYTES)
        var read = source.read(buffer)
        while (read >= 0 && copied <= limit) {
            job.ensureActive()
            copied += read
            digest.update(buffer, 0, read)
            output.write(buffer, 0, read)
            onProgress(copied.toFloat() / total)
            read = source.read(buffer)
        }
    }
    return HashedCopy(digest.digest().joinToString("") { "%02x".format(it) }, copied)
}

private fun open(context: Context, uri: Uri): InputStream =
    context.contentResolver.openInputStream(uri) ?: throw IOException("Unreadable")

private fun sizeOf(context: Context, uri: Uri): Long? =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        cursor.takeIf { it.moveToFirst() && !it.isNull(0) }?.getLong(0)
    }

private const val BUFFER_BYTES = 1 shl 20
