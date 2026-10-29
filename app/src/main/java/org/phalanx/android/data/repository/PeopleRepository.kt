package org.phalanx.android.data.repository

import kotlinx.coroutines.flow.StateFlow
import org.phalanx.android.data.model.PeopleLinks

/**
 * Which chats, across networks, the user said are one conversation: one person, or one group. Kept on this phone only;
 * nothing is linked without the user confirming it.
 */
interface PeopleRepository {
    /** Null until read from disk. */
    val links: StateFlow<PeopleLinks?>

    /** Puts [a] and [b], link keys of two chats, in one conversation, with any chat already linked to either. */
    suspend fun link(a: String, b: String)

    /** Takes a chat, by every key it may have been linked by, out of the conversation it was in. */
    suspend fun unlink(keys: Set<String>)

    /** Never suggests [a] and [b] as one person again. */
    suspend fun dismiss(a: String, b: String)
}
