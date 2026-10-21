package org.phalanx.android.data.repository

import kotlinx.coroutines.flow.Flow

/**
 * Users this account ignores: their messages and invites are hidden on every device signed in to
 * the account, because the list is kept on the server.
 */
interface BlockedUsersRepository {
    /** Matrix IDs, kept up to date as the list changes on any device. Empty when signed out. */
    val blocked: Flow<List<String>>

    suspend fun block(userId: String): Result<Unit>

    suspend fun unblock(userId: String): Result<Unit>
}
