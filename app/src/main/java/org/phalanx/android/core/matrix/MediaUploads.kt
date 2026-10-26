package org.phalanx.android.core.matrix

import java.time.Duration
import org.matrix.rustcomponents.sdk.AudioInfo
import org.matrix.rustcomponents.sdk.FileInfo
import org.matrix.rustcomponents.sdk.FormattedBody
import org.matrix.rustcomponents.sdk.ImageInfo
import org.matrix.rustcomponents.sdk.MessageFormat
import org.matrix.rustcomponents.sdk.SendAttachmentJoinHandle
import org.matrix.rustcomponents.sdk.ThumbnailInfo
import org.matrix.rustcomponents.sdk.Timeline
import org.matrix.rustcomponents.sdk.UploadParameters
import org.matrix.rustcomponents.sdk.UploadSource
import org.matrix.rustcomponents.sdk.VideoInfo
import org.phalanx.android.data.model.OutgoingMedia

/**
 * Queues [media] for upload through the SDK's send queue, which shows it in the timeline at once
 * and encrypts it in encrypted rooms. The returned handle completes once the upload is over.
 */
internal fun Timeline.upload(
    media: OutgoingMedia,
    caption: String?,
    captionHtml: String?,
    inReplyTo: String?
): SendAttachmentJoinHandle {
    val params = UploadParameters(
        source = UploadSource.File(media.file.path),
        caption = caption,
        formattedCaption = captionHtml?.let { FormattedBody(MessageFormat.Html, it) },
        mentions = null,
        inReplyTo = inReplyTo
    )
    val size = media.file.length().toULong()
    return when (media) {
        is OutgoingMedia.Image -> sendImage(
            params,
            thumbnailSource = null,
            imageInfo = ImageInfo(
                height = media.height.toULong(),
                width = media.width.toULong(),
                mimetype = media.mimeType,
                size = size,
                thumbnailInfo = null,
                thumbnailSource = null,
                blurhash = null,
                isAnimated = media.mimeType == "image/gif"
            )
        )
        is OutgoingMedia.Video -> sendVideo(
            params,
            thumbnailSource = media.thumbnail?.let { UploadSource.File(it.file.path) },
            videoInfo = VideoInfo(
                duration = media.durationMillis?.let(Duration::ofMillis),
                height = media.height?.toULong(),
                width = media.width?.toULong(),
                mimetype = media.mimeType,
                size = size,
                thumbnailInfo = media.thumbnail?.let {
                    ThumbnailInfo(it.height.toULong(), it.width.toULong(), it.mimeType, it.file.length().toULong())
                },
                thumbnailSource = null,
                blurhash = null
            )
        )
        is OutgoingMedia.Document -> sendFile(
            params,
            FileInfo(mimetype = media.mimeType, size = size, thumbnailInfo = null, thumbnailSource = null)
        )
        is OutgoingMedia.Voice -> sendVoiceMessage(
            params,
            AudioInfo(Duration.ofMillis(media.durationMillis), size, media.mimeType),
            media.waveform
        )
    }
}
