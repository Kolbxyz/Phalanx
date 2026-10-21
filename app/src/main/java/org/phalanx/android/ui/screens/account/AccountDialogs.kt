package org.phalanx.android.ui.screens.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.phalanx.android.R

@Composable
internal fun DisplayNameDialog(
    draft: String,
    saving: Boolean,
    failed: Boolean,
    onChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.account_display_name)) },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = onChange,
                singleLine = true,
                enabled = !saving,
                isError = failed,
                supportingText = if (failed) {
                    { Text(stringResource(R.string.account_name_failed)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave() })
            )
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = draft.isNotBlank() && !saving) {
                if (saving) LoadingIndicator(Modifier.size(20.dp)) else Text(stringResource(R.string.account_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.account_cancel)) }
        }
    )
}

/**
 * The one irreversible action in the app, so it asks for the password (the server requires it
 * anyway) and spells out the consequences instead of a bare "Are you sure?".
 */
@Composable
internal fun DeactivateDialog(
    draft: DeactivationDraft,
    onPassword: (String) -> Unit,
    onErase: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.account_deactivate_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.account_deactivate_body))
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
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.toggleable(
                        value = draft.erase,
                        enabled = !draft.busy,
                        role = Role.Checkbox,
                        onValueChange = onErase
                    )
                ) {
                    Checkbox(checked = draft.erase, onCheckedChange = null, enabled = !draft.busy)
                    Text(stringResource(R.string.account_deactivate_erase), modifier = Modifier.padding(start = 12.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = draft.canConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                if (draft.busy) LoadingIndicator(Modifier.size(20.dp)) else Text(stringResource(R.string.account_deactivate_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !draft.busy) { Text(stringResource(R.string.account_cancel)) }
        }
    )
}

/** Signing out deletes this device's keys, so the dialog says what that costs before it happens. */
@Composable
internal fun SignOutDialog(signingOut: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!signingOut) onDismiss() },
        title = { Text(stringResource(R.string.account_sign_out_title)) },
        text = { Text(stringResource(R.string.account_sign_out_body)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !signingOut,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                if (signingOut) LoadingIndicator(Modifier.size(20.dp)) else Text(stringResource(R.string.account_sign_out))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !signingOut) { Text(stringResource(R.string.account_cancel)) }
        }
    )
}
