package org.phalanx.android.core.matrix

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.matrix.rustcomponents.sdk.ClientException
import org.matrix.rustcomponents.sdk.use
import org.phalanx.android.data.model.MediaRef
import org.phalanx.android.data.repository.MediaRepository
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

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

    /** Server-supplied names can hold path separators; only the last segment is kept. */
    private fun safeName(filename: String): String =
        filename.substringAfterLast('/').substringAfterLast('\\').takeIf { it.isNotBlank() && it != ".." } ?: "media"

    private companion object {
        const val OPEN_FOLDER = "open"
    }
}
