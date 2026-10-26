package org.phalanx.android.data.repository

import kotlinx.coroutines.flow.StateFlow
import org.phalanx.android.data.model.PeopleLinks

/**
 * Which one-to-one chats, across networks, the user said are one person. Kept on this phone only;
 * nothing is linked without the user confirming it.
 */
interface PeopleRepository {
    /** Null until read from disk. */
    val links: StateFlow<PeopleLinks?>

    /** Puts [a] and [b], Matrix IDs of two chats, in one person, with anyone already linked to either. */
    suspend fun link(a: String, b: String)

    /** Takes [userId] out of the person it was linked into. */
    suspend fun unlink(userId: String)

    /** Never suggests [a] and [b] as one person again. */
    suspend fun dismiss(a: String, b: String)
}
