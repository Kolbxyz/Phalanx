package org.phalanx.android.core.matrix

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.matrix.rustcomponents.sdk.Client
import org.matrix.rustcomponents.sdk.ClientBuilder
import org.matrix.rustcomponents.sdk.ClientSessionDelegate
import org.matrix.rustcomponents.sdk.CrossProcessLockConfig
import org.matrix.rustcomponents.sdk.RequestConfig
import org.matrix.rustcomponents.sdk.Session
import org.matrix.rustcomponents.sdk.SqliteStoreBuilder
import java.io.File
import java.util.Base64
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Where one account's SDK stores live. Data survives cache clearing; cache does not. */
internal data class SessionPaths(val data: File, val cache: File)

/** The only place SDK clients are configured, so sign-in and restore cannot drift apart. */
@Singleton
internal class ClientFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    sessionStore: SessionStore
) {
    private val sessionDelegate = StoreSessionDelegate(sessionStore)

    fun newDirectoryId(): String = UUID.randomUUID().toString()

    fun pathsFor(directoryId: String) = SessionPaths(
        data = File(context.filesDir, "sessions/$directoryId").apply { mkdirs() },
        cache = File(context.cacheDir, "sessions/$directoryId").apply { mkdirs() }
    )

    /** A builder for a client that has not signed in yet; [configure] picks the homeserver. */
    fun builder(paths: SessionPaths, storeKey: ByteArray): ClientBuilder = ClientBuilder()
        .sqliteStore(
            SqliteStoreBuilder(paths.data.absolutePath, paths.cache.absolutePath).key(storeKey)
        )
        .setSessionDelegate(sessionDelegate)
        .userAgent(USER_AGENT)
        .autoEnableBackups(true)
        .autoEnableCrossSigning(true)
        .requestConfig(
            RequestConfig(
                timeout = REQUEST_TIMEOUT_MILLIS,
                // Must be non-zero for the SDK to retry, including after a 429 Too Many Requests.
                retryLimit = RETRY_LIMIT,
                maxConcurrentRequests = null,
                maxRetryTime = null
            )
        )
        // The app is a single process, so the SDK does not need a cross-process lock.
        .crossProcessLockConfig(CrossProcessLockConfig.SingleProcess)

    /** Reopens a signed-in account. Needs no network. */
    suspend fun restore(data: SessionData): Client {
        val storeKey = Base64.getDecoder().decode(data.storeKey)
        val client = builder(pathsFor(data.directoryId), storeKey)
            .homeserverUrl(data.homeserverUrl)
            .build()
        client.restoreSession(data.toSdkSession())
        return client
    }

    private companion object {
        const val USER_AGENT = "Phalanx/0.1 (Android)"
        const val REQUEST_TIMEOUT_MILLIS = 30_000uL
        const val RETRY_LIMIT = 3uL
    }
}

/** Persists the tokens the SDK refreshes on its own, so a restart does not sign the user out. */
private class StoreSessionDelegate(private val store: SessionStore) : ClientSessionDelegate {
    override fun retrieveSessionFromKeychain(userId: String): Session {
        val data = checkNotNull(store.load()?.takeIf { it.userId == userId }) { "No stored session for this user" }
        return data.toSdkSession()
    }

    override fun saveSessionInKeychain(session: Session) {
        store.update {
            it.copy(
                accessToken = session.accessToken,
                refreshToken = session.refreshToken,
                oauthData = session.oauthData
            )
        }
    }
}
