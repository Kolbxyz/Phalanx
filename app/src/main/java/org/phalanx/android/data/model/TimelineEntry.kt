package org.phalanx.android.data.model

import androidx.compose.runtime.Immutable

/** One row of a room's timeline. [id] is stable for the life of the row, including while it is still sending. */
@Immutable
sealed interface TimelineEntry {
    val id: String

    data class DayDivider(override val id: String, val millis: Long) : TimelineEntry

    data class Message(
        override val id: String,
        /** Null while a message of this device's is still being sent, which then has [transactionId]. */
        val eventId: String?,
        val transactionId: String?,
        val senderId: String,
        /** Disambiguated with the Matrix ID when another member uses the same name. */
        val senderName: String,
        val senderAvatar: MediaRef?,
        val isOwn: Boolean,
        val body: MessageBody,
        val replyTo: ReplyPreview?,
        val reactions: List<ReactionSummary>,
        val timestampMillis: Long,
        val isEdited: Boolean,
        val sendState: SendState,
        val canReply: Boolean,
        val canEdit: Boolean,
        /** The other members whose public read receipt sits on this message: it is where they stopped. */
        val readBy: Set<String> = emptySet()
    ) : TimelineEntry
}

/**
 * The message being answered. [senderName] and [body] are null until the SDK has fetched it, and
 * stay null with [isUnavailable] when it could not (deleted, or from before this account joined).
 */
@Immutable
data class ReplyPreview(
    val senderName: String?,
    val body: MessageBody?,
    val isUnavailable: Boolean = false,
    /** The quoted event, to jump to; null for the composer's own preview. */
    val eventId: String? = null
)

/** [senderIds] in the order they reacted. [image] when the key is a custom emote (an `mxc://` URL). */
@Immutable
data class ReactionSummary(
    val key: String,
    val count: Int,
    val byMe: Boolean,
    val senderIds: List<String>,
    val image: MediaRef? = null
)

enum class SendState { Sent, Sending, Failed }
