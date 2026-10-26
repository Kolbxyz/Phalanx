package org.phalanx.android.core.matrix

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.matrix.rustcomponents.sdk.Client
import org.matrix.rustcomponents.sdk.ClientBuildException
import org.matrix.rustcomponents.sdk.ClientException
import org.matrix.rustcomponents.sdk.Room
import org.matrix.rustcomponents.sdk.SyncService
import org.matrix.rustcomponents.sdk.TaskHandle
import org.phalanx.android.data.model.SessionState
import uniffi.matrix_sdk.SearchBackfillStrategy

/**
 * Owns the one open SDK client and its sync service. Opening, closing, starting and stopping all
 * go through [lock], so a sign-out can never race a sync that is starting or stopping.
 */
@Singleton
internal class ActiveClient @Inject constructor(
    private val factory: ClientFactory,
    private val sessionStore: SessionStore
) {
    private val lock = Mutex()

    @Volatile
    private var client: Client? = null
    private var sync: SyncService? = null
    private var backfill: TaskHandle? = null

    private val mutableState = MutableStateFlow<SessionState>(SessionState.Restoring)
    val state: StateFlow<SessionState> = mutableState

    private val mutableSyncService = MutableStateFlow<SyncService?>(null)

    /** The sync service while signed in, for flows that hang off it. Null otherwise. */
    val syncService: StateFlow<SyncService?> = mutableSyncService

    suspend fun restore() {
        lock.withLock {
            if (client != null) return@withLock
            val data = sessionStore.load()
            if (data == null) {
                mutableState.value = SessionState.LoggedOut
            } else {
                openLocked(data)
            }
        }
    }

    /** Persists [data] and opens it; the caller must have closed whatever client created it. */
    suspend fun adopt(data: SessionData) {
        lock.withLock {
            sessionStore.save(data)
            openLocked(data)
        }
    }

    /** The SDK room for [roomId], or null if this account does not know it. Caller owns the handle. */
    fun room(roomId: String): Room? = client?.getRoom(roomId)

    /** The signed-in client, or null. Borrowed, never closed by the caller: [signOut] owns its end. */
    fun client(): Client? = client

    suspend fun startSync() {
        lock.withLock {
            sync?.start()
            // The search index only sees messages as they sync; this pages in older history,
            // throttled to leave room for the user's own traffic, and only while the app is open.
            if (backfill == null) backfill = client?.runSearchBackfill(SearchBackfillStrategy.FOREGROUND)
        }
    }

    suspend fun stopSync() {
        lock.withLock {
            sync?.stop()
            stopBackfillLocked()
        }
    }

    private fun stopBackfillLocked() {
        backfill?.let {
            it.cancel()
            it.destroy()
        }
        backfill = null
    }

    suspend fun signOut() {
        lock.withLock { signOutLocked() }
    }

    private suspend fun signOutLocked() {
        val data = sessionStore.load()
        try {
            client?.logout()
        } catch (_: ClientException) {
            // Best effort: with no network the server keeps the device listed until the user
            // removes it elsewhere, but this device must still forget the account locally.
        }
        closeLocked()
        sessionStore.clear()
        data?.let { removeDirectories(it.directoryId) }
        mutableState.value = SessionState.LoggedOut
    }

    private suspend fun openLocked(data: SessionData) {
        try {
            val opened = factory.restore(data)
            val service = opened.syncService().withSharePos(true).withOfflineMode().finish()
            client = opened
            sync = service
            mutableSyncService.value = service
            mutableState.value = SessionState.LoggedIn(data.userId)
        } catch (_: ClientBuildException) {
            discardUnusable(data)
        } catch (_: ClientException) {
            discardUnusable(data)
        }
    }

    /** A store that cannot be reopened cannot be repaired, so the account is signed out locally. */
    private fun discardUnusable(data: SessionData) {
        sessionStore.clear()
        removeDirectories(data.directoryId)
        mutableState.value = SessionState.LoggedOut
    }

    private suspend fun closeLocked() {
        stopBackfillLocked()
        mutableSyncService.value = null
        sync?.let {
            it.stop()
            it.destroy()
        }
        client?.close()
        sync = null
        client = null
    }

    private fun removeDirectories(directoryId: String) {
        val paths = factory.pathsFor(directoryId)
        paths.data.deleteRecursively()
        paths.cache.deleteRecursively()
    }
}
