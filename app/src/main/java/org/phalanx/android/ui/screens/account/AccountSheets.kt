package org.phalanx.android.ui.screens.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.ui.components.ModalSheet
import org.phalanx.android.ui.theme.Symbols

/** Signing out deletes this device's keys, so the sheet says what that costs before it happens. */
@Composable
internal fun SignOutSheet(signingOut: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AccountActionSheet(
        icon = Symbols.Logout,
        title = stringResource(R.string.account_sign_out_title),
        body = stringResource(R.string.account_sign_out_body),
        confirm = stringResource(R.string.account_sign_out),
        busyLabel = stringResource(R.string.account_signing_out),
        busy = signingOut,
        canConfirm = !signingOut,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

/**
 * The one irreversible action in the app, so it asks for the password (the server requires it
 * anyway) and spells out the consequences instead of a bare "Are you sure?".
 */
@Composable
internal fun DeactivateSheet(
    draft: DeactivationDraft,
    onPassword: (String) -> Unit,
    onErase: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AccountActionSheet(
        icon = Symbols.Delete,
        title = stringResource(R.string.account_deactivate_title),
        body = stringResource(R.string.account_deactivate_body),
        confirm = stringResource(R.string.account_deactivate_confirm),
        busyLabel = stringResource(R.string.account_deactivating),
        busy = draft.busy,
        canConfirm = draft.canConfirm,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    ) {
        OutlinedTextField(
            value = draft.password,
            onValueChange = onPassword,
            label = { Text(stringResource(R.string.login_password)) },
            singleLine = true,
            enabled = !draft.busy,
            isError = draft.wrongPassword || draft.failed,
            supportingText = when {
                draft.wrongPassword -> {
                    { Text(stringResource(R.string.account_wrong_password)) }
                }
                draft.failed -> {
                    { Text(stringResource(R.string.account_deactivate_failed)) }
                }
                else -> null
            },
            shape = RoundedCornerShape(16.dp),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = draft.erase, enabled = !draft.busy, role = Role.Checkbox, onValueChange = onErase)
        ) {
            Checkbox(checked = draft.erase, onCheckedChange = null, enabled = !draft.busy)
            Text(stringResource(R.string.account_deactivate_erase), modifier = Modifier.padding(start = 12.dp))
        }
    }
}

/**
 * One shape for every account action that can't be taken back: what it is, what it costs, then a
 * full-width button in the error colour above Cancel. It cannot be swiped away while [busy].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountActionSheet(
    icon: ImageVector,
    title: String,
    body: String,
    confirm: String,
    busyLabel: String,
    busy: Boolean,
    canConfirm: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    fields: @Composable ColumnScope.() -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val working by rememberUpdatedState(busy)
    val sheet = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !working }
    )
    ModalSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = sheet) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = colors.errorContainer,
                contentColor = colors.onErrorContainer
            ) {
                Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null) }
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
            fields()
            Button(
                onClick = onConfirm,
                enabled = canConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = colors.error, contentColor = colors.onError),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                if (busy) {
                    LoadingIndicator(Modifier.size(24.dp), color = colors.onError)
                    Text(busyLabel, modifier = Modifier.padding(start = 8.dp))
                } else {
                    Text(confirm)
                }
            }
            FilledTonalButton(
                onClick = onDismiss,
                enabled = !busy,
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = colors.surfaceContainerHighest),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text(stringResource(R.string.account_cancel)) }
        }
    }
}
