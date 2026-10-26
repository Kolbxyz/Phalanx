package org.phalanx.android.core.matrix

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.matrix.rustcomponents.sdk.ClientException
import org.matrix.rustcomponents.sdk.use
import org.phalanx.android.data.model.MediaRef
import org.phalanx.android.data.repository.MediaRepository

@Singleton
internal class RustMediaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val active: ActiveClient
) : MediaRepository {
    // Decrypted copies only live until the next launch: viewer apps read them right away.
    private val folder = File(context.cacheDir, OPEN_FOLDER).apply { deleteRecursively() }

    override suspend fun openable(media: MediaRef, filename: String, mimeType: String?): Result<Uri> =
        withContext(Dispatchers.IO) {
            val client = active.client() ?: return@withContext Result.failure(IOException("Signed out"))
            try {
                val bytes = media.toSource().use { client.getMediaContent(it) }
                // A fresh folder per file keeps the original name (players show it) without clashes.
                val file = File(File(folder, UUID.randomUUID().toString()).apply { mkdirs() }, safeName(filename))
                file.writeBytes(bytes)
                Result.success(FileProvider.getUriForFile(context, "${context.packageName}.files", file))
            } catch (e: ClientException) {
                Result.failure(e)
            } catch (e: IOException) {
                Result.failure(e)
            }
        }

    override suspend fun saveToGallery(media: MediaRef, filename: String, mimeType: String?): Result<Unit> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return Result.failure(UnsupportedOperationException())
        return save(media) { bytes ->
            val video = mimeType?.startsWith("video/") == true
            val collection = if (video) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, safeName(filename))
                mimeType?.let { put(MediaStore.MediaColumns.MIME_TYPE, it) }
                val root = if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$root/$GALLERY_FOLDER")
                // Hidden from other apps until fully written.
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(collection, values) ?: throw IOException("MediaStore refused the file")
            try {
                resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: throw IOException("No output stream")
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } catch (e: IOException) {
                resolver.delete(uri, null, null)
                throw e
            }
        }
    }

    override suspend fun saveTo(media: MediaRef, destination: Uri): Result<Unit> = save(media) { bytes ->
        context.contentResolver.openOutputStream(destination)?.use { it.write(bytes) }
            ?: throw IOException("No output stream")
    }

    private suspend fun save(media: MediaRef, write: (ByteArray) -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        val client = active.client() ?: return@withContext Result.failure(IOException("Signed out"))
        try {
            write(media.toSource().use { client.getMediaContent(it) })
            Result.success(Unit)
        } catch (e: ClientException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    /** Server-supplied names can hold path separators; only the last segment is kept. */
    private fun safeName(filename: String): String =
        filename.substringAfterLast('/').substringAfterLast('\\').takeIf { it.isNotBlank() && it != ".." } ?: "media"

    private companion object {
        const val OPEN_FOLDER = "open"
        const val GALLERY_FOLDER = "Phalanx"
    }
}
