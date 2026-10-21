package org.phalanx.android.data.model

import java.io.File

/**
 * An attachment read off the device and ready to upload. Files live in the app's private cache
 * and are deleted once the upload is over, whether it succeeded or not.
 */
sealed interface OutgoingMedia {
    val file: File
    val mimeType: String

    /** Re-encoded on the device, so its EXIF (location, camera, time taken) never leaves it. */
    data class Image(
        override val file: File,
        override val mimeType: String,
        val width: Int,
        val height: Int
    ) : OutgoingMedia

    data class Video(
        override val file: File,
        override val mimeType: String,
        val width: Int?,
        val height: Int?,
        val durationMillis: Long?,
        val thumbnail: Image?
    ) : OutgoingMedia

    data class Document(
        override val file: File,
        override val mimeType: String
    ) : OutgoingMedia

    /** [waveform] holds values from 0 to 1, one per sample taken while recording. */
    data class Voice(
        override val file: File,
        override val mimeType: String,
        val durationMillis: Long,
        val waveform: List<Float>
    ) : OutgoingMedia
}
