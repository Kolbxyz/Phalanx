package org.phalanx.android.ui.screens.login

import android.content.ActivityNotFoundException
import androidx.annotation.StringRes
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.phalanx.android.R
import org.phalanx.android.data.model.AuthFailure

@Composable
fun LoginScreen(viewModel: LoginViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(R.string.login_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        when (state.step) {
            LoginStep.Server -> ServerStep(state, viewModel)
            LoginStep.Credentials -> CredentialsStep(state, viewModel)
        }
        state.failure?.let {
            Text(
                text = stringResource(it.messageRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun ServerStep(state: LoginUiState, viewModel: LoginViewModel) {
    OutlinedTextField(
        value = state.server,
        onValueChange = viewModel::onServerChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.login_server_label)) },
        supportingText = { Text(stringResource(R.string.login_server_supporting)) },
        singleLine = true,
        isError = state.failure != null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onGo = { viewModel.continueWithServer() })
    )
    ActionButton(R.string.login_continue, state.canContinue, state.busy, viewModel::continueWithServer)
}

@Composable
private fun CredentialsStep(state: LoginUiState, viewModel: LoginViewModel) {
    if (state.methods.oidc) BrowserSignIn(state, viewModel)
    if (state.methods.password) {
        if (state.methods.oidc) {
            Text(
                text = stringResource(R.string.login_or_password),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        PasswordFields(state, viewModel)
    }
    TextButton(onClick = viewModel::changeServer, enabled = !state.busy) {
        Text(stringResource(R.string.login_back))
    }
}

/** The server's own sign-in page (OAuth), opened in a Custom Tab so this app never sees the password. */
@Composable
private fun BrowserSignIn(state: LoginUiState, viewModel: LoginViewModel) {
    val context = LocalContext.current
    LaunchedEffect(state.browserUrl) {
        val url = state.browserUrl ?: return@LaunchedEffect
        try {
            CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, url.toUri())
            viewModel.onBrowserOpened()
        } catch (_: ActivityNotFoundException) {
            viewModel.onBrowserUnavailable()
        }
    }
    if (state.awaitingBrowser) {
        Text(
            text = stringResource(R.string.login_oidc_waiting),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(onClick = viewModel::cancelBrowser, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            if (state.busy) LoadingIndicator(modifier = Modifier.size(24.dp)) else Text(stringResource(R.string.login_oidc_cancel))
        }
    } else {
        ActionButton(R.string.login_oidc_continue, state.canUseBrowser, state.busy, viewModel::signInWithBrowser)
    }
}

@Composable
private fun PasswordFields(state: LoginUiState, viewModel: LoginViewModel) {
    OutlinedTextField(
        value = state.username,
        onValueChange = viewModel::onUsernameChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.login_username)) },
        singleLine = true,
        isError = state.failure != null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next, autoCorrectEnabled = false)
    )
    OutlinedTextField(
        value = state.password,
        onValueChange = viewModel::onPasswordChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.login_password)) },
        singleLine = true,
        isError = state.failure != null,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onGo = { viewModel.signIn() })
    )
    // A secondary button when the browser route is offered too, so only one action reads as primary.
    if (state.methods.oidc) {
        OutlinedButton(onClick = viewModel::signIn, enabled = state.canSignIn, modifier = Modifier.fillMaxWidth()) {
            if (state.busy) LoadingIndicator(modifier = Modifier.size(24.dp)) else Text(stringResource(R.string.login_sign_in))
        }
    } else {
        ActionButton(R.string.login_sign_in, state.canSignIn, state.busy, viewModel::signIn)
    }
}

@Composable
private fun ActionButton(@StringRes label: Int, enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        if (busy) LoadingIndicator(modifier = Modifier.size(24.dp)) else Text(stringResource(label))
    }
}

@StringRes
private fun AuthFailure.messageRes(): Int = when (this) {
    AuthFailure.InvalidServer -> R.string.login_error_invalid_server
    AuthFailure.ServerUnreachable -> R.string.login_error_unreachable
    AuthFailure.NoSupportedLogin -> R.string.login_error_no_supported_login
    AuthFailure.InvalidCredentials -> R.string.login_error_invalid_credentials
    AuthFailure.AccountDisabled -> R.string.login_error_disabled
    AuthFailure.RateLimited -> R.string.login_error_rate_limited
    AuthFailure.BrowserUnavailable -> R.string.login_error_no_browser
    // Never shown: the view model drops it, the user chose to back out.
    AuthFailure.Cancelled,
    AuthFailure.Unknown -> R.string.login_error_unknown
}
