package org.phalanx.android.core.matrix

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import org.matrix.rustcomponents.sdk.Client
import org.matrix.rustcomponents.sdk.ClientException
import org.matrix.rustcomponents.sdk.IgnoredUsersListener
import org.phalanx.android.data.repository.BlockedUsersRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class RustBlockedUsersRepository @Inject constructor(
    private val active: ActiveClient
) : BlockedUsersRepository {
    // A new sync service means a new sign-in, whose client has its own list.
    @OptIn(ExperimentalCoroutinesApi::class)
    override val blocked: Flow<List<String>> = active.syncService.flatMapLatest {
        active.client()?.ignoredUserIds() ?: flowOf(emptyList())
    }

    override suspend fun block(userId: String): Result<Unit> = withClient { it.ignoreUser(userId) }

    override suspend fun unblock(userId: String): Result<Unit> = withClient { it.unignoreUser(userId) }

    private suspend fun withClient(block: suspend (Client) -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        val client = active.client() ?: return@withContext Result.failure(IOException("Signed out"))
        try {
            block(client)
            Result.success(Unit)
        } catch (e: ClientException) {
            Result.failure(e)
        }
    }
}

/** The current list first, then every change, until the collector stops. */
private fun Client.ignoredUserIds(): Flow<List<String>> = callbackFlow {
    val handle = subscribeToIgnoredUsers(
        object : IgnoredUsersListener {
            override fun call(ignoredUserIds: List<String>) {
                trySend(ignoredUserIds.sorted())
            }
        }
    )
    try {
        trySend(ignoredUsers().sorted())
    } catch (_: ClientException) {
        // The listener still reports the list once the server answers.
    }
    awaitClose {
        handle.cancel()
        handle.destroy()
    }
}
