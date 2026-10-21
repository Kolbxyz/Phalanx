package org.phalanx.android.ui.screens.login

import androidx.compose.runtime.Immutable
import org.phalanx.android.data.model.AuthFailure
import org.phalanx.android.data.model.LoginMethods

enum class LoginStep { Server, Credentials }

@Immutable
data class LoginUiState(
    val step: LoginStep = LoginStep.Server,
    val server: String = "",
    val methods: LoginMethods = LoginMethods(password = false, oidc = false),
    val username: String = "",
    val password: String = "",
    val busy: Boolean = false,
    /** The sign-in page the screen should open next; cleared once it is open. */
    val browserUrl: String? = null,
    /** True while the server's sign-in page is open and the redirect has not come back. */
    val awaitingBrowser: Boolean = false,
    val failure: AuthFailure? = null
) {
    val canContinue: Boolean get() = server.isNotBlank() && !busy
    val canSignIn: Boolean get() = username.isNotBlank() && password.isNotEmpty() && !busy && !awaitingBrowser
    val canUseBrowser: Boolean get() = !busy && !awaitingBrowser
}
