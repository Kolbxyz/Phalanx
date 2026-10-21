package org.phalanx.android.data.repository

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.StateFlow
import org.phalanx.android.data.model.MediaRef
import org.phalanx.android.data.model.MessageBody

/** One message matching a search, in any room. */
@Immutable
data class MessageHit(
    val roomId: String,
    val eventId: String,
    val senderName: String,
    val senderAvatar: MediaRef?,
    val body: MessageBody,
    val timestampMillis: Long
)

/**
 * Message search over an index kept on this device (encrypted at rest), so encrypted rooms are
 * searchable and no query leaves the phone. Only messages this device has seen are indexed.
 */
interface SearchRepository {
    /** A new search, or null when signed out. The caller closes it. */
    suspend fun open(): MessageSearch?
}

interface MessageSearch {
    /** Best matches first; empty for a blank query. */
    val hits: StateFlow<List<MessageHit>>

    /** True while a page of results loads. */
    val loading: StateFlow<Boolean>

    val endReached: StateFlow<Boolean>

    /** Starts over with [query]; a blank query clears the results. */
    suspend fun setQuery(query: String): Result<Unit>

    suspend fun loadMore(): Result<Unit>

    fun close()
}
