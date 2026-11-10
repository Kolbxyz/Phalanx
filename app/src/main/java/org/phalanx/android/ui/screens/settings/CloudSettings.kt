package org.phalanx.android.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.phalanx.android.R
import org.phalanx.android.ui.components.SegmentedRow
import org.phalanx.android.ui.components.segmentedShape
import org.phalanx.android.ui.theme.Symbols

/**
 * Writing with Claude in place of the model on this phone: the user's own API key, then the
 * switch. Turning it on sends messages out of the phone, so it asks first, and needs a key.
 */
@Composable
internal fun CloudSettings(enabled: Boolean, onEnabled: (Boolean) -> Unit, ai: AiSettingsViewModel) {
    val hasKey by ai.cloudKey.collectAsStateWithLifecycle()
    var askKey by rememberSaveable { mutableStateOf(false) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SegmentedRow(
            headline = stringResource(R.string.settings_cloud_key),
            supporting = stringResource(
                if (hasKey) R.string.settings_cloud_key_added else R.string.settings_cloud_key_none
            ),
            shape = segmentedShape(0, 2),
            leading = { Icon(Symbols.Key, contentDescription = null) },
            interaction = Modifier.clickable(role = Role.Button) { askKey = true }
        )
        SegmentedRow(
            headline = stringResource(R.string.settings_cloud_title),
            supporting = stringResource(R.string.settings_cloud_summary),
            shape = segmentedShape(1, 2),
            leading = { Icon(Symbols.Summarize, contentDescription = null) },
            interaction = Modifier.toggleable(value = enabled, role = Role.Switch) { on ->
                when {
                    !on -> onEnabled(false)
                    hasKey -> confirm = true
                    else -> askKey = true
                }
            },
            trailing = {
                Switch(
                    checked = enabled,
                    onCheckedChange = null,
                    thumbContent = if (enabled) {
                        { Icon(Symbols.Check, null, Modifier.size(SwitchDefaults.IconSize)) }
                    } else {
                        null
                    }
                )
            }
        )
    }
    if (askKey) {
        CloudKeyDialog(
            hasKey = hasKey,
            onSave = {
                ai.setCloudKey(it)
                askKey = false
            },
            onRemove = {
                // Without a key nothing can be written in the cloud, so the switch goes off with it.
                onEnabled(false)
                ai.removeCloudKey()
                askKey = false
            },
            onDismiss = { askKey = false }
        )
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.settings_cloud_confirm_title)) },
            text = { Text(stringResource(R.string.settings_cloud_confirm_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEnabled(true)
                        confirm = false
                    }
                ) { Text(stringResource(R.string.settings_cloud_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

// The key is typed hidden and never shown again: a kept one can only be replaced or removed.
@Composable
private fun CloudKeyDialog(hasKey: Boolean, onSave: (String) -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_cloud_key)) },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                supportingText = { Text(stringResource(R.string.settings_cloud_key_hint)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (draft.isNotBlank()) onSave(draft) })
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft) }, enabled = draft.isNotBlank()) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            if (hasKey) {
                TextButton(onClick = onRemove) { Text(stringResource(R.string.settings_cloud_key_remove)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    )
}
