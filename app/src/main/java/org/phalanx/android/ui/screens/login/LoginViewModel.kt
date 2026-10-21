package org.phalanx.android.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.phalanx.android.core.matrix.OidcCallbacks
import org.phalanx.android.data.model.AuthException
import org.phalanx.android.data.model.AuthFailure
import org.phalanx.android.data.repository.AuthRepository
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val callbacks: OidcCallbacks
) : ViewModel() {
    private val mutableState = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            callbacks.callback.filterNotNull().collect { url ->
                callbacks.consume(url)
                completeOidc(url)
            }
        }
    }

    fun onServerChange(value: String) = mutableState.update { it.copy(server = value, failure = null) }

    fun onUsernameChange(value: String) = mutableState.update { it.copy(username = value, failure = null) }

    fun onPasswordChange(value: String) = mutableState.update { it.copy(password = value, failure = null) }

    fun changeServer() {
        cancelBrowser()
        mutableState.update { it.copy(step = LoginStep.Server, password = "", failure = null) }
    }

    fun continueWithServer() {
        val current = mutableState.value
        if (!current.canContinue) return
        mutableState.update { it.copy(busy = true, failure = null) }
        viewModelScope.launch {
            auth.checkHomeserver(current.server).fold(
                onSuccess = { methods ->
                    mutableState.update { it.copy(step = LoginStep.Credentials, methods = methods, busy = false) }
                },
                onFailure = { fail(it) }
            )
        }
    }

    fun signIn() {
        val current = mutableState.value
        if (!current.canSignIn) return
        mutableState.update { it.copy(busy = true, failure = null) }
        viewModelScope.launch {
            auth.signIn(current.username, current.password).fold(
                // The session state flips to LoggedIn and this screen is replaced; drop the
                // password from memory first.
                onSuccess = { mutableState.update { it.copy(busy = false, password = "") } },
                onFailure = { fail(it) }
            )
        }
    }

    fun signInWithBrowser() {
        if (!mutableState.value.canUseBrowser) return
        mutableState.update { it.copy(busy = true, failure = null) }
        viewModelScope.launch {
            auth.startOidc().fold(
                onSuccess = { url -> mutableState.update { it.copy(busy = false, browserUrl = url, awaitingBrowser = true) } },
                onFailure = { fail(it) }
            )
        }
    }

    fun onBrowserOpened() = mutableState.update { it.copy(browserUrl = null) }

    fun onBrowserUnavailable() {
        cancelBrowser()
        mutableState.update { it.copy(failure = AuthFailure.BrowserUnavailable) }
    }

    fun cancelBrowser() {
        if (!mutableState.value.awaitingBrowser) return
        mutableState.update { it.copy(browserUrl = null, awaitingBrowser = false) }
        viewModelScope.launch { auth.cancelOidc() }
    }

    private fun completeOidc(url: String) {
        mutableState.update { it.copy(busy = true, failure = null) }
        viewModelScope.launch {
            auth.completeOidc(url).fold(
                onSuccess = { mutableState.update { it.copy(busy = false, awaitingBrowser = false) } },
                onFailure = { fail(it) }
            )
        }
    }

    private fun fail(error: Throwable) = mutableState.update {
        val failure = (error as? AuthException)?.failure ?: AuthFailure.Unknown
        it.copy(
            busy = false,
            awaitingBrowser = false,
            browserUrl = null,
            failure = failure.takeUnless { f -> f == AuthFailure.Cancelled }
        )
    }
}
