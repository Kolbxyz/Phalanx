package org.phalanx.android.core.matrix

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.matrix.rustcomponents.sdk.Client
import org.matrix.rustcomponents.sdk.ClientException
import org.matrix.rustcomponents.sdk.MessageLikeEventContent
import org.matrix.rustcomponents.sdk.NotificationEvent
import org.matrix.rustcomponents.sdk.NotificationItem
import org.matrix.rustcomponents.sdk.TimelineEventContent
import org.matrix.rustcomponents.sdk.use
import org.phalanx.android.data.model.IncomingMessage

/** The reaction this notification is about, or null when it is about something else. */
internal fun NotificationItem.reaction(): MessageLikeEventContent.ReactionContent? {
    val timeline = event as? NotificationEvent.Timeline ?: return null
    val content = timeline.event.content() as? TimelineEventContent.MessageLike ?: return null
    return content.content as? MessageLikeEventContent.ReactionContent
}

/**
 * A reaction to one of this account's own messages, as a notification line; null when it reacts to
 * someone else's, which the account's reaction push rule notifies for too. [body] of the result is
 * the message reacted to, and [IncomingMessage.reaction] the emoji or text it was given.
 */
internal suspend fun Client.reactionToOwn(
    roomId: String,
    eventId: String,
    item: NotificationItem,
    reaction: MessageLikeEventContent.ReactionContent
): IncomingMessage? {
    val timeline = item.event as? NotificationEvent.Timeline ?: return null
    val reacted = try {
        ownMessage(roomId, reaction.relatedEventId)
    } catch (_: ClientException) {
        // The message reacted to can't be fetched, so whose it is can't be told: better none.
        null
    }
    return reacted?.let { message ->
        IncomingMessage(
            roomId = roomId,
            eventId = eventId,
            roomName = item.roomInfo.displayName,
            isDirect = item.roomInfo.isDm,
            senderName = item.senderInfo.displayName ?: timeline.event.senderId(),
            body = message.messageType.toBody(isReply = message.inReplyToEventId != null),
            timestampMillis = timeline.event.timestamp().toLong(),
            // A reaction is worth seeing, not worth a sound.
            isNoisy = false,
            reaction = keyOf(item.rawEvent).orEmpty()
        )
    }
}

// [eventId] in [roomId] when this account sent it and it is a message; null otherwise.
private suspend fun Client.ownMessage(roomId: String, eventId: String): MessageLikeEventContent.RoomMessage? {
    val own = userId()
    val event = getRoom(roomId)?.use { it.loadOrFetchEvent(eventId) }
    return event?.use { reacted ->
        val content = (reacted.content() as? TimelineEventContent.MessageLike)?.content
        (content as? MessageLikeEventContent.RoomMessage)?.takeIf { reacted.senderId() == own }
    }
}

/** The reaction's key (its emoji or text), from the decrypted event: the bindings don't expose it. */
internal fun keyOf(rawEvent: String): String? = try {
    Json.parseToJsonElement(rawEvent).jsonObject["content"]?.jsonObject
        ?.get("m.relates_to")?.jsonObject
        ?.get("key")?.jsonPrimitive?.contentOrNull
        ?.take(MAX_KEY_CHARS)
} catch (_: SerializationException) {
    null
} catch (_: IllegalArgumentException) {
    // Not the documented shape: a notification without the emoji rather than a guess.
    null
}

// A reaction is an emoji or a short text; a longer key is cut, never shown whole.
private const val MAX_KEY_CHARS = 32
