package org.phalanx.android.core.matrix

import org.matrix.rustcomponents.sdk.FormattedBody
import org.matrix.rustcomponents.sdk.MessageContent
import org.matrix.rustcomponents.sdk.MessageFormat
import org.matrix.rustcomponents.sdk.MessageType
import org.matrix.rustcomponents.sdk.MsgLikeKind
import org.phalanx.android.core.format.Markdown
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.model.UnsupportedKind

/** Upper bound of an MSC3246 waveform sample. */
private const val WAVEFORM_MAX = 1024f

/** [isReply] lets plain-text bodies drop the quoted fallback some bridges still prepend. */
internal fun MsgLikeKind.toBody(isReply: Boolean): MessageBody = when (this) {
    is MsgLikeKind.Message -> content.toBody(isReply)
    is MsgLikeKind.Sticker -> MessageBody.Image(
        media = source.toRef(),
        thumbnail = info.thumbnailSource?.toRef(),
        width = info.width?.toInt(),
        height = info.height?.toInt(),
        caption = null,
        isSticker = true,
        filename = body,
        mimeType = info.mimetype
    )
    is MsgLikeKind.Redacted -> MessageBody.Redacted
    is MsgLikeKind.UnableToDecrypt -> MessageBody.Undecryptable
    is MsgLikeKind.Poll -> MessageBody.Unsupported(UnsupportedKind.Poll)
    is MsgLikeKind.LiveLocation -> MessageBody.Unsupported(UnsupportedKind.LiveLocation)
    is MsgLikeKind.Other -> MessageBody.Unsupported(UnsupportedKind.Other)
}

private fun MessageContent.toBody(isReply: Boolean): MessageBody = msgType.toBody(isReply)

internal fun MessageType.toBody(isReply: Boolean): MessageBody = when (val type = this) {
    is MessageType.Text -> text(type.content.body, type.content.formatted, MessageBody.TextStyle.Normal, isReply)
    is MessageType.Notice -> text(type.content.body, type.content.formatted, MessageBody.TextStyle.Notice, isReply)
    is MessageType.Emote -> text(type.content.body, type.content.formatted, MessageBody.TextStyle.Emote, isReply)
    is MessageType.Image -> with(type.content) {
        MessageBody.Image(
            media = source.toRef(),
            thumbnail = info?.thumbnailSource?.toRef(),
            width = info?.width?.toInt(),
            height = info?.height?.toInt(),
            caption = caption,
            isSticker = false,
            filename = filename,
            mimeType = info?.mimetype
        )
    }
    is MessageType.Video -> with(type.content) {
        MessageBody.Video(
            media = source.toRef(),
            thumbnail = info?.thumbnailSource?.toRef(),
            width = info?.width?.toInt(),
            height = info?.height?.toInt(),
            durationMillis = info?.duration?.toMillis(),
            filename = filename,
            mimeType = info?.mimetype,
            caption = caption
        )
    }
    is MessageType.Audio -> with(type.content) {
        MessageBody.Audio(
            media = source.toRef(),
            filename = filename,
            mimeType = info?.mimetype,
            durationMillis = (audio?.duration ?: info?.duration)?.toMillis(),
            waveform = audio?.waveform?.map { (it.toFloat() / WAVEFORM_MAX).coerceIn(0f, 1f) },
            isVoice = voice != null
        )
    }
    is MessageType.File -> with(type.content) {
        MessageBody.File(
            media = source.toRef(),
            filename = filename,
            mimeType = info?.mimetype,
            sizeBytes = info?.size?.toLong(),
            caption = caption
        )
    }
    is MessageType.Location -> MessageBody.Location(type.content.geoUri, type.content.description)
    is MessageType.Gallery -> MessageBody.Unsupported(UnsupportedKind.Gallery)
    is MessageType.Other -> MessageBody.Text(type.body, html = null, style = MessageBody.TextStyle.Normal)
}

private fun text(
    body: String,
    formatted: FormattedBody?,
    style: MessageBody.TextStyle,
    isReply: Boolean
): MessageBody.Text {
    val html = formatted?.takeIf { it.format is MessageFormat.Html }?.body?.let(HtmlSanitizer::sanitizeWithEmotes)
    val plain = if (isReply) HtmlSanitizer.stripPlainReplyFallback(body) else body
    // An emote that isn't a valid mxc:// URL keeps its index, and shows its alt text instead.
    val emotes = html?.emoteUrls?.map(::mediaRefOf).orEmpty()
    return MessageBody.Text(plain, html?.html?.takeIf { it.isNotBlank() } ?: markdownOf(plain), style, emotes)
}

/** Bodies sent without HTML (some bridges, plain clients) still show their markdown. */
private fun markdownOf(plain: String): String? = Markdown.render(plain).html?.let(HtmlSanitizer::sanitize)
