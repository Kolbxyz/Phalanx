package org.phalanx.android.data.repository

import kotlinx.coroutines.flow.StateFlow
import org.phalanx.android.data.model.TimelineEntry

/** The room is unknown to the signed-in account, or its timeline could not be opened. */
class TimelineUnavailableException(cause: Throwable? = null) : Exception("Timeline unavailable", cause)

interface TimelineRepository {
    suspend fun open(roomId: String): Result<TimelineSession>
}

/** A live view of one room. The repository owns its lifetime; screens only read and act on it. */
interface TimelineSession {
    val title: String
    val isDirect: Boolean

    /** Oldest first. Includes messages still being sent. */
    val entries: StateFlow<List<TimelineEntry>>

    /** False once the start of the room has been reached. */
    val hasMoreHistory: StateFlow<Boolean>

    /** Sends [text] as markdown, as a reply to [replyTo] when given. */
    suspend fun send(text: String, replyTo: TimelineEntry.Message? = null): Result<Unit>

    /** Adds the reaction [key], or removes it when this account already reacted with it. */
    suspend fun toggleReaction(message: TimelineEntry.Message, key: String): Result<Unit>

    suspend fun redact(message: TimelineEntry.Message): Result<Unit>

    /** Loads one more page of history. A failure is transient; scrolling again retries. */
    suspend fun loadOlder(): Result<Unit>

    suspend fun markRead(): Result<Unit>
}
