package org.phalanx.android.data.repository

import kotlinx.coroutines.flow.Flow
import org.phalanx.android.data.model.Bookmark

/**
 * The account's saved messages, kept in its Matrix account data so every device sees them. Each
 * change is written to the server first and kept here only once that worked.
 */
interface BookmarkRepository {
    /** Every bookmark, newest first. */
    val bookmarks: Flow<List<Bookmark>>

    /** Takes the server's list as the truth, for bookmarks added or removed on another device. */
    suspend fun refresh(): Result<Unit>

    suspend fun add(bookmark: Bookmark): Result<Unit>

    suspend fun remove(eventId: String): Result<Unit>
}
