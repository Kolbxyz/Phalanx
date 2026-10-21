package org.phalanx.android.core.matrix

import org.matrix.rustcomponents.sdk.EventSendState
import org.matrix.rustcomponents.sdk.EventTimelineItem
import org.matrix.rustcomponents.sdk.MessageContent
import org.matrix.rustcomponents.sdk.MessageType
import org.matrix.rustcomponents.sdk.MsgLikeKind
import org.matrix.rustcomponents.sdk.ProfileDetails
import org.matrix.rustcomponents.sdk.TimelineItem
import org.matrix.rustcomponents.sdk.TimelineItemContent
import org.matrix.rustcomponents.sdk.VirtualTimelineItem
import org.matrix.rustcomponents.sdk.use
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.model.SendState
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.data.model.UnsupportedKind

/**
 * Copies what the UI needs out of an SDK item and releases it. Null for items that are not shown
 * (membership changes, state events, read markers, ...).
 */
internal fun TimelineItem.toEntry(): TimelineEntry? = use { item ->
    val id = item.uniqueId().id
    item.asEvent()?.toMessage(id) ?: (item.asVirtual() as? VirtualTimelineItem.DateDivider)?.let {
        TimelineEntry.DayDivider(id, it.ts.toLong())
    }
}

private fun EventTimelineItem.toMessage(id: String): TimelineEntry.Message? {
    val msgLike = (content as? TimelineItemContent.MsgLike)?.content ?: return null
    val message = (msgLike.kind as? MsgLikeKind.Message)?.content
    return TimelineEntry.Message(
        id = id,
        senderName = (senderProfile as? ProfileDetails.Ready)?.displayName ?: sender,
        isOwn = isOwn,
        body = bodyOf(msgLike.kind),
        timestampMillis = timestamp.toLong(),
        isEdited = message?.isEdited ?: false,
        sendState = when (localSendState) {
            null, is EventSendState.Sent -> SendState.Sent
            is EventSendState.NotSentYet -> SendState.Sending
            is EventSendState.SendingFailed -> SendState.Failed
        }
    )
}

private fun bodyOf(kind: MsgLikeKind): MessageBody = when (kind) {
    is MsgLikeKind.Message -> kind.content.toBody()
    is MsgLikeKind.Redacted -> MessageBody.Redacted
    is MsgLikeKind.UnableToDecrypt -> MessageBody.Undecryptable
    is MsgLikeKind.Sticker -> MessageBody.Unsupported(UnsupportedKind.Sticker)
    is MsgLikeKind.Poll -> MessageBody.Unsupported(UnsupportedKind.Poll)
    is MsgLikeKind.LiveLocation -> MessageBody.Unsupported(UnsupportedKind.Location)
    is MsgLikeKind.Other -> MessageBody.Unsupported(UnsupportedKind.Other)
}

private fun MessageContent.toBody(): MessageBody = when (msgType) {
    is MessageType.Text, is MessageType.Notice -> MessageBody.Text(body)
    is MessageType.Emote -> MessageBody.Text("* $body")
    is MessageType.Image -> MessageBody.Unsupported(UnsupportedKind.Image)
    is MessageType.Video -> MessageBody.Unsupported(UnsupportedKind.Video)
    is MessageType.Audio -> MessageBody.Unsupported(UnsupportedKind.Audio)
    is MessageType.File -> MessageBody.Unsupported(UnsupportedKind.File)
    is MessageType.Location -> MessageBody.Unsupported(UnsupportedKind.Location)
    is MessageType.Gallery -> MessageBody.Unsupported(UnsupportedKind.Gallery)
    is MessageType.Other -> MessageBody.Unsupported(UnsupportedKind.Other)
}
