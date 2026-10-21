package org.phalanx.android.core.matrix

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.matrix.rustcomponents.sdk.Client
import org.matrix.rustcomponents.sdk.ClientBuildException
import org.matrix.rustcomponents.sdk.ClientException
import org.matrix.rustcomponents.sdk.ErrorKind
import org.matrix.rustcomponents.sdk.OAuthException
import org.matrix.rustcomponents.sdk.OAuthPrompt
import org.matrix.rustcomponents.sdk.SlidingSyncVersionBuilder
import org.matrix.rustcomponents.sdk.use
import org.phalanx.android.data.model.AuthException
import org.phalanx.android.data.model.AuthFailure
import org.phalanx.android.data.model.LoginMethods
import org.phalanx.android.data.model.SessionState
import org.phalanx.android.data.repository.AuthRepository
import uniffi.matrix_sdk.OAuthAuthorizationData
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class RustAuthRepository @Inject constructor(
    private val factory: ClientFactory,
    private val active: ActiveClient
) : AuthRepository {
    override val sessionState: StateFlow<SessionState> = active.state

    // Serialises the sign-in steps, so a cancel cannot free the OAuth data a completion is using.
    private val lock = Mutex()

    /** The client that resolved the homeserver and is waiting for credentials or a redirect. */
    private var pending: PendingLogin? = null

    override suspend fun restoreSession() = active.restore()

    override suspend fun checkHomeserver(input: String): Result<LoginMethods> = locked {
        discardPending()
        val directoryId = factory.newDirectoryId()
        val storeKey = ByteArray(STORE_KEY_BYTES).also(SecureRandom()::nextBytes)
        var client: Client? = null
        try {
            client = factory.builder(factory.pathsFor(directoryId), storeKey)
                .serverNameOrHomeserverUrl(input.trim())
                .slidingSyncVersionBuilder(SlidingSyncVersionBuilder.DISCOVER_NATIVE)
                .build()
            val methods = client.homeserverLoginDetails().use {
                LoginMethods(password = it.supportsPasswordLogin(), oidc = it.supportsOauthLogin())
            }
            if (methods.password || methods.oidc) {
                pending = PendingLogin(client, directoryId, storeKey)
                Result.success(methods)
            } else {
                dispose(client, directoryId)
                Result.failure(AuthException(AuthFailure.NoSupportedLogin))
            }
        } catch (e: ClientBuildException) {
            dispose(client, directoryId)
            Result.failure(AuthException(e.toFailure(), e))
        } catch (e: ClientException) {
            dispose(client, directoryId)
            Result.failure(AuthException(e.toFailure(), e))
        }
    }

    override suspend fun signIn(username: String, password: String): Result<Unit> = locked {
        val login = pending ?: return@locked Result.failure(AuthException(AuthFailure.Unknown))
        try {
            login.client.login(username.trim(), password, DEVICE_NAME, null)
            adopt(login)
            Result.success(Unit)
        } catch (e: ClientException) {
            Result.failure(AuthException(e.toFailure(), e))
        }
    }

    override suspend fun startOidc(): Result<String> = locked {
        val login = pending ?: return@locked Result.failure(AuthException(AuthFailure.Unknown))
        try {
            login.abortOAuth()
            val data = login.client.urlForOauth(
                oauthConfiguration = OAuthClient.configuration(),
                prompt = OAuthPrompt.Consent,
                loginHint = null,
                deviceId = null,
                additionalScopes = emptyList()
            )
            login.oauth = data
            Result.success(data.loginUrl())
        } catch (e: OAuthException) {
            Result.failure(AuthException(e.toFailure(), e))
        }
    }

    override suspend fun completeOidc(callbackUrl: String): Result<Unit> = locked {
        val login = pending?.takeIf { it.oauth != null }
            // A redirect with no sign-in waiting: the app was restarted while the browser was open.
            ?: return@locked Result.failure(AuthException(AuthFailure.Unknown))
        try {
            login.client.loginWithOauthCallback(callbackUrl)
            login.oauth?.close()
            login.oauth = null
            adopt(login)
            Result.success(Unit)
        } catch (e: OAuthException) {
            login.abortOAuth()
            Result.failure(AuthException(e.toFailure(), e))
        } catch (e: ClientException) {
            login.abortOAuth()
            Result.failure(AuthException(e.toFailure(), e))
        }
    }

    override suspend fun cancelOidc() {
        locked { pending?.abortOAuth() }
    }

    private suspend fun <T> locked(block: suspend () -> T): T =
        withContext(Dispatchers.IO) { lock.withLock { block() } }

    /** Hands the signed-in client's session to [ActiveClient], which reopens it as the live account. */
    private suspend fun adopt(login: PendingLogin) {
        val session = login.client.session()
        val data = SessionData(
            accessToken = session.accessToken,
            refreshToken = session.refreshToken,
            userId = session.userId,
            deviceId = session.deviceId,
            homeserverUrl = session.homeserverUrl,
            oauthData = session.oauthData,
            storeKey = Base64.getEncoder().encodeToString(login.storeKey),
            directoryId = login.directoryId
        )
        // The sign-in client and the one adopt() opens share the same SQLite stores, which must
        // never be open at the same time.
        login.client.close()
        pending = null
        active.adopt(data)
    }

    private suspend fun discardPending() {
        pending?.let {
            it.abortOAuth()
            dispose(it.client, it.directoryId)
        }
        pending = null
    }

    private fun dispose(client: Client?, directoryId: String) {
        client?.close()
        factory.pathsFor(directoryId).let {
            it.data.deleteRecursively()
            it.cache.deleteRecursively()
        }
    }

    private class PendingLogin(val client: Client, val directoryId: String, val storeKey: ByteArray) {
        /** The OAuth sign-in in flight, if any; aborting it lets the server drop its state. */
        var oauth: OAuthAuthorizationData? = null

        suspend fun abortOAuth() {
            oauth?.let {
                client.abortOauthAuth(it)
                it.close()
            }
            oauth = null
        }
    }

    private companion object {
        const val DEVICE_NAME = "Phalanx Android"
        const val STORE_KEY_BYTES = 32
    }
}

private fun ClientBuildException.toFailure(): AuthFailure = when (this) {
    is ClientBuildException.InvalidServerName,
    is ClientBuildException.WellKnownDeserializationException -> AuthFailure.InvalidServer
    is ClientBuildException.ServerUnreachable,
    is ClientBuildException.WellKnownLookupFailed -> AuthFailure.ServerUnreachable
    else -> AuthFailure.Unknown
}

private fun ClientException.toFailure(): AuthFailure = when (this) {
    is ClientException.MatrixApi -> when (kind) {
        is ErrorKind.Forbidden, is ErrorKind.InvalidUsername, is ErrorKind.Unauthorized -> AuthFailure.InvalidCredentials
        is ErrorKind.UserDeactivated, is ErrorKind.UserLocked, is ErrorKind.UserSuspended -> AuthFailure.AccountDisabled
        is ErrorKind.LimitExceeded -> AuthFailure.RateLimited
        is ErrorKind.ConnectionFailed, is ErrorKind.ConnectionTimeout -> AuthFailure.ServerUnreachable
        else -> AuthFailure.Unknown
    }
    else -> AuthFailure.Unknown
}

private fun OAuthException.toFailure(): AuthFailure = when (this) {
    is OAuthException.Cancelled -> AuthFailure.Cancelled
    is OAuthException.NotSupported, is OAuthException.MetadataInvalid -> AuthFailure.NoSupportedLogin
    else -> AuthFailure.Unknown
}
