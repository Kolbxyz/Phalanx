package org.phalanx.android.data.repository

import kotlinx.coroutines.flow.StateFlow
import org.phalanx.android.data.model.LoginMethods
import org.phalanx.android.data.model.SessionState

/** Failures arrive as [org.phalanx.android.data.model.AuthException]. */
interface AuthRepository {
    val sessionState: StateFlow<SessionState>

    /** Opens the stored session, if there is one. Safe to call more than once. */
    suspend fun restoreSession()

    /** Resolves [input] (a server name or URL) and reports how it lets accounts sign in. */
    suspend fun checkHomeserver(input: String): Result<LoginMethods>

    /** Signs in with a password on the server last accepted by [checkHomeserver]. */
    suspend fun signIn(username: String, password: String): Result<Unit>

    /** Starts OAuth sign-in on the server last accepted by [checkHomeserver]; returns the page to open. */
    suspend fun startOidc(): Result<String>

    /** Finishes the sign-in [startOidc] began, from the redirect URI the browser handed back. */
    suspend fun completeOidc(callbackUrl: String): Result<Unit>

    /** Forgets a sign-in [startOidc] began, when the user gives up on the browser. */
    suspend fun cancelOidc()
}
