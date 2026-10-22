package org.phalanx.android.core.media

import android.graphics.Bitmap
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.os.Build
import okio.IOException

/**
 * The first frame of a video held in memory, scaled to fit [maxPx], for videos sent without a
 * thumbnail (Discord's GIFs are short MP4s). Read from memory, so no decrypted copy reaches disk.
 */
internal fun firstFrame(video: ByteArray, maxPx: Int?): Bitmap {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(ByteSource(video))
        val frame = if (maxPx != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, maxPx, maxPx)
        } else {
            retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        }
        return frame ?: throw IOException("No video frame")
    } catch (e: IllegalArgumentException) {
        // Thrown for data the platform cannot parse as a video.
        throw IOException("Unreadable video", e)
    } finally {
        retriever.release()
    }
}

private class ByteSource(private val bytes: ByteArray) : MediaDataSource() {
    override fun getSize(): Long = bytes.size.toLong()

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position >= bytes.size) return END_OF_STREAM
        val count = minOf(size, bytes.size - position.toInt())
        System.arraycopy(bytes, position.toInt(), buffer, offset, count)
        return count
    }

    override fun close() = Unit
}

private const val END_OF_STREAM = -1
