package org.phalanx.android.core.matrix

import org.matrix.rustcomponents.sdk.EmbeddedEventDetails
import org.matrix.rustcomponents.sdk.EventOrTransactionId
import org.matrix.rustcomponents.sdk.EventSendState
import org.matrix.rustcomponents.sdk.EventTimelineItem
import org.matrix.rustcomponents.sdk.InReplyToDetails
import org.matrix.rustcomponents.sdk.MsgLikeContent
import org.matrix.rustcomponents.sdk.MsgLikeKind
import org.matrix.rustcomponents.sdk.ProfileDetails
import org.matrix.rustcomponents.sdk.Reaction
import org.matrix.rustcomponents.sdk.TimelineItem
import org.matrix.rustcomponents.sdk.TimelineItemContent
import org.matrix.rustcomponents.sdk.VirtualTimelineItem
import org.matrix.rustcomponents.sdk.use
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.model.ReactionSummary
import org.phalanx.android.data.model.ReplyPreview
import org.phalanx.android.data.model.SendState
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.data.model.UnsupportedKind

/** What mapping needs from the session beyond the item itself. */
internal class MappingContext(
    val ownUserId: String,
    /**
     * Asked with the ID of a reply whose quoted event the SDK has not loaded yet; the reply's item
     * updates once it has.
     */
    val onMissingReply: (replyEventId: String) -> Unit
)

/**
 * Copies what the UI needs out of an SDK item and releases it. Null for items that are not shown
 * (membership changes, state events, read markers, ...).
 */
internal fun TimelineItem.toEntry(context: MappingContext): TimelineEntry? = use { item ->
    val id = item.uniqueId().id
    item.asEvent()?.use { it.toMessage(id, context) }
        ?: (item.asVirtual() as? VirtualTimelineItem.DateDivider)?.let { TimelineEntry.DayDivider(id, it.ts.toLong()) }
}

private fun EventTimelineItem.toMessage(id: String, context: MappingContext): TimelineEntry.Message? {
    val msgLike = (content as? TimelineItemContent.MsgLike)?.content ?: return null
    val reply = msgLike.inReplyTo
    val eventId = (eventOrTransactionId as? EventOrTransactionId.EventId)?.eventId
    return TimelineEntry.Message(
        id = id,
        eventId = eventId,
        transactionId = (eventOrTransactionId as? EventOrTransactionId.TransactionId)?.transactionId,
        senderId = sender,
        senderName = nameOf(sender, senderProfile),
        senderAvatar = mediaRefOf((senderProfile as? ProfileDetails.Ready)?.avatarUrl),
        isOwn = isOwn,
        body = withGifFlag(msgLike.kind.toBody(isReply = reply != null)),
        replyTo = reply?.toPreview(eventId, context),
        reactions = reactions.map { it.toSummary(context.ownUserId) },
        timestampMillis = timestamp.toLong(),
        isEdited = msgLike.isEdited(),
        sendState = when (localSendState) {
            null, is EventSendState.Sent -> SendState.Sent
            is EventSendState.NotSentYet -> SendState.Sending
            is EventSendState.SendingFailed -> SendState.Failed
        },
        canReply = canBeRepliedTo,
        canEdit = isEditable,
        readBy = readReceipts.keys - context.ownUserId
    )
}

private fun MsgLikeContent.isEdited(): Boolean =
    (kind as? MsgLikeKind.Message)?.content?.isEdited ?: false

/** The display name, with the Matrix ID added when two members share it. */
internal fun nameOf(userId: String, profile: ProfileDetails?): String {
    val ready = profile as? ProfileDetails.Ready ?: return userId
    val name = ready.displayName?.takeIf { it.isNotBlank() } ?: return userId
    return if (ready.displayNameAmbiguous) "$name ($userId)" else name
}

/** [replyEventId] is the reply's own ID: the SDK fills the details in on the item that quotes. */
private fun InReplyToDetails.toPreview(replyEventId: String?, context: MappingContext): ReplyPreview =
    when (val details = event()) {
        is EmbeddedEventDetails.Ready -> {
            // Anything but a message (a poll, a state change) still gets a label, never "loading".
            val body = ((details.content as? TimelineItemContent.MsgLike)?.content?.kind)?.toBody(isReply = true)
                ?: MessageBody.Unsupported(UnsupportedKind.Other)
            details.content.destroy()
            ReplyPreview(nameOf(details.sender, details.senderProfile), body, eventId = eventId())
        }
        EmbeddedEventDetails.Unavailable -> {
            // A local echo has no event ID yet; its remote echo arrives with the details loaded.
            replyEventId?.let(context.onMissingReply)
            ReplyPreview(senderName = null, body = null, eventId = eventId())
        }
        EmbeddedEventDetails.Pending -> ReplyPreview(senderName = null, body = null, eventId = eventId())
        is EmbeddedEventDetails.Error ->
            ReplyPreview(senderName = null, body = null, isUnavailable = true, eventId = eventId())
    }

private fun Reaction.toSummary(ownUserId: String) =
    ReactionSummary(
        key = key,
        count = senders.size,
        byMe = senders.any { it.senderId == ownUserId },
        senderIds = senders.sortedBy { it.timestamp }.map { it.senderId },
        // Bridges (Discord's custom emoji, say) react with an image; its key is the image's URL.
        image = key.takeIf { it.startsWith("mxc://") }?.let(::mediaRefOf)
    )
