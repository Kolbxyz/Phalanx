package org.phalanx.android.data.repository

import kotlinx.coroutines.flow.StateFlow

/**
 * Users whose messages never notify this account, kept as server push rules so every signed-in
 * client honours them. Mentions of this account still notify. Their messages stay visible.
 */
interface MutedUsersRepository {
    /** Matrix IDs; empty until first loaded or when signed out. */
    val muted: StateFlow<Set<String>>

    suspend fun mute(userId: String): Result<Unit>

    suspend fun unmute(userId: String): Result<Unit>
}
