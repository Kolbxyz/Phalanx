package org.phalanx.android.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.core.graphics.scale
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.phalanx.android.core.prefs.SettingsStore
import org.phalanx.android.data.model.OutgoingMedia
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Copies what the user picked or captured into the app's private cache, in the form it is uploaded.
 * Both folders are wiped on every launch: an upload interrupted by the process dying is not resumed.
 */
@Singleton
class AttachmentPreparer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore
) {
    private val uploads = File(context.cacheDir, UPLOAD_FOLDER).apply { deleteRecursively() }
    private val captures = File(context.cacheDir, CAPTURE_FOLDER).apply { deleteRecursively() }

    /** Where the camera app writes a new photo or video; it is granted write access to this one file. */
    fun captureUri(video: Boolean): Uri {
        val file = File(captures.apply { mkdirs() }, "${UUID.randomUUID()}.${if (video) "mp4" else "jpg"}")
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    /** A new empty file in its own folder, so [name] reaches the server as the user would expect it. */
    fun newFile(name: String): File = File(File(uploads, UUID.randomUUID().toString()).apply { mkdirs() }, name)

    /** Deletes [media]'s files once its upload is over. */
    fun discard(media: OutgoingMedia) {
        media.file.parentFile?.deleteRecursively()
        if (media is OutgoingMedia.Video) media.thumbnail?.let(::discard)
    }

    suspend fun prepare(uri: Uri): Result<OutgoingMedia> = withContext(Dispatchers.IO) {
        try {
            val mimeType = context.contentResolver.getType(uri) ?: DEFAULT_MIME
            val media = when {
                mimeType == GIF_MIME -> copyImage(uri)
                mimeType.startsWith("image/") -> encodeImage(uri, nameOf(uri), settings.current().fullQualityImages)
                mimeType.startsWith("video/") -> copyVideo(uri, mimeType)
                else -> OutgoingMedia.Document(copy(uri, nameOf(uri)), mimeType)
            }
            Result.success(media)
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: SecurityException) {
            Result.failure(e)
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        }
    }

    /** [image] with the user's [edits] applied to the file as sent; the original file is left for the caller to discard. */
    suspend fun edit(image: OutgoingMedia.Image, edits: ImageEdits): Result<OutgoingMedia.Image> =
        withContext(Dispatchers.IO) {
            try {
                val edited = decodeImageFile(image.file, FULL_IMAGE_SIDE).edited(edits)
                Result.success(writeImage(edited, image.file.nameWithoutExtension, quality = FULL_QUALITY))
            } catch (e: IOException) {
                Result.failure(e)
            } catch (e: OutOfMemoryError) {
                Result.failure(IOException("Image too large to edit", e))
            }
        }

    /** Scaled down and re-encoded; PNG when the picture has transparency, so it isn't lost. */
    private fun encodeImage(uri: Uri, name: String, full: Boolean): OutgoingMedia.Image {
        val bitmap = context.contentResolver.decodeScaled(uri, if (full) FULL_IMAGE_SIDE else MAX_IMAGE_SIDE)
        return writeImage(bitmap, name.substringBeforeLast('.'), quality = if (full) FULL_QUALITY else JPEG_QUALITY)
    }

    private fun writeImage(
        bitmap: Bitmap,
        baseName: String,
        allowPng: Boolean = true,
        quality: Int = JPEG_QUALITY
    ): OutgoingMedia.Image {
        val png = allowPng && bitmap.hasAlpha()
        val file = newFile("$baseName.${if (png) "png" else "jpg"}")
        file.outputStream().use {
            bitmap.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, quality, it)
        }
        return OutgoingMedia.Image(file, if (png) "image/png" else "image/jpeg", bitmap.width, bitmap.height)
    }

    /** Re-encoding would lose the animation; GIFs carry no location data to strip. */
    private fun copyImage(uri: Uri): OutgoingMedia.Image {
        val file = copy(uri, nameOf(uri))
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        return OutgoingMedia.Image(file, GIF_MIME, bounds.outWidth, bounds.outHeight)
    }

    private fun copyVideo(uri: Uri, mimeType: String): OutgoingMedia.Video {
        val file = copy(uri, nameOf(uri))
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.path)
            val width = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val height = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            // Phones record sideways and store the rotation; the size shown must be the upright one.
            val turned = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION) in TURNED
            val thumbnail = retriever.frameAtTime?.let { frame ->
                val longest = maxOf(frame.width, frame.height)
                val scale = minOf(1f, MAX_THUMBNAIL_SIDE.toFloat() / longest)
                val scaled = frame.scale((frame.width * scale).toInt(), (frame.height * scale).toInt())
                writeImage(scaled, "thumbnail", allowPng = false)
            }
            return OutgoingMedia.Video(
                file = file,
                mimeType = mimeType,
                width = if (turned) height else width,
                height = if (turned) width else height,
                durationMillis = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong(),
                thumbnail = thumbnail
            )
        } finally {
            retriever.release()
        }
    }

    private fun MediaMetadataRetriever.metadata(key: Int): Int? = extractMetadata(key)?.toIntOrNull()

    private fun copy(uri: Uri, name: String): File {
        val file = newFile(name)
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Unreadable attachment")
        input.use { source -> file.outputStream().use { source.copyTo(it) } }
        return file
    }

    /** The name the picking app shows, reduced to its last segment; providers may put anything there. */
    private fun nameOf(uri: Uri): String {
        val shown = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        return shown?.substringAfterLast('/')?.takeIf { it.isNotBlank() && it != ".." }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() && it != ".." }
            ?: DEFAULT_NAME
    }

    private companion object {
        const val UPLOAD_FOLDER = "upload"
        const val CAPTURE_FOLDER = "capture"
        const val DEFAULT_MIME = "application/octet-stream"
        const val DEFAULT_NAME = "attachment"
        const val GIF_MIME = "image/gif"
        const val MAX_IMAGE_SIDE = 2048
        const val FULL_IMAGE_SIDE = 4096
        const val FULL_QUALITY = 95
        const val MAX_THUMBNAIL_SIDE = 800
        const val JPEG_QUALITY = 85
        val TURNED = setOf(90, 270)
    }
}
