package org.phalanx.android.core.matrix

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.matrix.rustcomponents.sdk.ClientException
import org.matrix.rustcomponents.sdk.EmoteMessageContent
import org.matrix.rustcomponents.sdk.EventOrTransactionId
import org.matrix.rustcomponents.sdk.FormattedBody
import org.matrix.rustcomponents.sdk.Mentions
import org.matrix.rustcomponents.sdk.MessageFormat
import org.matrix.rustcomponents.sdk.MessageType
import org.matrix.rustcomponents.sdk.NoticeMessageContent
import org.matrix.rustcomponents.sdk.RoomException
import org.matrix.rustcomponents.sdk.RoomMessageEventContentWithoutRelation
import org.matrix.rustcomponents.sdk.TextMessageContent
import org.matrix.rustcomponents.sdk.messageEventContentNew
import org.matrix.rustcomponents.sdk.use
import org.phalanx.android.core.format.OutgoingMessage
import org.phalanx.android.data.model.Member
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.data.repository.NotForwardableException

/** Markdown follows the user's setting; mentions are rendered and listed either way. */
internal suspend fun RustTimelineSession.contentOf(
    text: String,
    mentions: List<Member>
): RoomMessageEventContentWithoutRelation {
    val prefs = settings.current()
    val message = OutgoingMessage.of(text, mentions, prefs.markdown, emotes.byShortcode(), prefs.stripLinkTrackers)
    val formatted = message.html?.let { FormattedBody(MessageFormat.Html, it) }
    val type = when (message.kind) {
        OutgoingMessage.Kind.Text -> MessageType.Text(TextMessageContent(message.body, formatted))
        OutgoingMessage.Kind.Emote -> MessageType.Emote(EmoteMessageContent(message.body, formatted))
        OutgoingMessage.Kind.Notice -> MessageType.Notice(NoticeMessageContent(message.body, formatted))
    }
    return messageEventContentNew(type).use { it.withMentions(Mentions(message.userIds, message.mentionsRoom)) }
}

internal fun TimelineEntry.Message.target(): EventOrTransactionId? =
    eventId?.let { EventOrTransactionId.EventId(it) }
        ?: transactionId?.let { EventOrTransactionId.TransactionId(it) }

internal suspend fun io(block: suspend () -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
    try {
        runCatchingSdk(block)
    } catch (e: NotForwardableException) {
        Result.failure(e)
    }
}

internal suspend fun runCatchingSdk(block: suspend () -> Unit): Result<Unit> = try {
    block()
    Result.success(Unit)
} catch (e: ClientException) {
    Result.failure(e)
} catch (e: RoomException) {
    Result.failure(e)
}

/** Sends [eventId]'s content again through [to]'s timeline, as Element X does. */
internal suspend fun RustTimelineSession.forward(eventId: String, to: RustTimelineSession): Result<Unit> = io {
    val content = timeline.forwardableContent(eventId) ?: throw NotForwardableException()
    to.timeline.send(content).close()
}
