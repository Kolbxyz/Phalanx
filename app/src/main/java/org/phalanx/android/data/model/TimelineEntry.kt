package org.phalanx.android.data.model

import androidx.compose.runtime.Immutable

/** One row of a room's timeline. [id] is stable for the life of the row, including while it is still sending. */
@Immutable
sealed interface TimelineEntry {
    val id: String

    data class DayDivider(override val id: String, val millis: Long) : TimelineEntry

    data class Message(
        override val id: String,
        val senderName: String,
        val isOwn: Boolean,
        val body: MessageBody,
        val timestampMillis: Long,
        val isEdited: Boolean,
        val sendState: SendState
    ) : TimelineEntry
}

@Immutable
sealed interface MessageBody {
    data class Text(val text: String) : MessageBody

    /** A message of a kind this build cannot show yet. */
    data class Unsupported(val kind: UnsupportedKind) : MessageBody

    data object Redacted : MessageBody

    /** Encrypted for keys this device does not have (yet). */
    data object Undecryptable : MessageBody
}

enum class UnsupportedKind { Image, Video, Audio, File, Location, Sticker, Poll, Gallery, Other }

enum class SendState { Sent, Sending, Failed }
