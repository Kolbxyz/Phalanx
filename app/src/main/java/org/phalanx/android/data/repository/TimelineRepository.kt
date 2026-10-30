package org.phalanx.android.data.repository

import kotlinx.coroutines.flow.StateFlow
import org.phalanx.android.data.model.CustomEmote
import org.phalanx.android.data.model.Member
import org.phalanx.android.data.model.OutgoingMedia
import org.phalanx.android.data.model.TimelineEntry

/** The room is unknown to the signed-in account, or its timeline could not be opened. */
class TimelineUnavailableException(cause: Throwable? = null) : Exception("Timeline unavailable", cause)

/** The message is not one that can be sent again elsewhere (a poll, a state change, ...). */
class NotForwardableException : Exception("Not forwardable")

interface TimelineRepository {
    suspend fun open(roomId: String): Result<TimelineSession>

    /**
     * The events this account's read receipts in [roomId] point at, public and private, from any
     * of its devices: the latest of them in the timeline is where the user stopped reading. Empty
     * when none is known.
     */
    suspend fun readUpTo(roomId: String): Result<Set<String>>

    /** Sends a copy of [eventId] from [fromRoomId] to [toRoomId]; media is not uploaded again. */
    suspend fun forward(fromRoomId: String, eventId: String, toRoomId: String): Result<Unit>
}

/** A live view of one room. The repository owns its lifetime; screens only read and act on it. */
interface TimelineSession {
    val title: String
    val isDirect: Boolean

    /** Oldest first. Includes messages still being sent. */
    val entries: StateFlow<List<TimelineEntry>>

    /** The other members typing right now. */
    val typing: StateFlow<List<Member>>

    /** Tells the others whether this account is typing; callers respect the user's setting. */
    fun setTyping(typing: Boolean)

    /** False once the start of the room has been reached. */
    val hasMoreHistory: StateFlow<Boolean>

    /**
     * Sends [text] as markdown, as a reply to [replyTo] when given. "@" and the name of one of
     * [mentions] becomes a pill that notifies them.
     */
    suspend fun send(
        text: String,
        mentions: List<Member> = emptyList(),
        replyTo: TimelineEntry.Message? = null
    ): Result<Unit>

    /**
     * Uploads [media] with an optional [caption]; returns once the upload is over. It carries on, and
     * its files are deleted afterwards, even if the caller stops waiting.
     */
    suspend fun sendMedia(media: OutgoingMedia, caption: String?, replyTo: TimelineEntry.Message?): Result<Unit>

    /** Replaces the text of this account's [message] with [text], as markdown, as [send] does. */
    suspend fun edit(message: TimelineEntry.Message, text: String, mentions: List<Member> = emptyList()): Result<Unit>

    /** The unsent text kept for this room, if any. Stored by the SDK, encrypted with the session. */
    suspend fun loadDraft(): String?

    /** Keeps [text] as the room's draft, or clears it when blank. Does not wait for the write. */
    fun saveDraft(text: String)

    /** Adds the reaction [key], or removes it when this account already reacted with it. */
    suspend fun toggleReaction(message: TimelineEntry.Message, key: String): Result<Unit>

    suspend fun redact(message: TimelineEntry.Message): Result<Unit>

    /** Retries this account's [message] after it failed to send; does nothing once it went out. */
    suspend fun retrySend(message: TimelineEntry.Message): Result<Unit>

    /** Pins or unpins [message] for everyone in the room; needs the right to change pins. */

    suspend fun setPinned(message: TimelineEntry.Message, pinned: Boolean): Result<Unit>

    /** Loads one more page of history. A failure is transient; scrolling again retries. */
    suspend fun loadOlder(): Result<Unit>

    /** The members who joined, roles first, to mention; this account left out. Empty if unavailable. */
    suspend fun joinedMembers(): List<Member>

    /** The custom emoji that make sense here, this room's first; ":shortcode:" in a sent text shows them. */
    suspend fun emotes(): List<CustomEmote>

    /** Who [userIds] are in this room, in the same order. */
    suspend fun members(userIds: List<String>): List<Member>

    suspend fun markRead(): Result<Unit>
}
