package org.phalanx.android.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.phalanx.android.R

/** Edits the push gateway; saving an empty field goes back to automatic. */
@Composable
internal fun GatewayDialog(current: String, invalid: Boolean, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Hub, contentDescription = null) },
        title = { Text(stringResource(R.string.push_gateway)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.push_gateway_body, PUBLIC_GATEWAY_HOST))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.push_gateway_label)) },
                    placeholder = { Text(stringResource(R.string.push_gateway_automatic)) },
                    isError = invalid,
                    supportingText = if (invalid) {
                        { Text(stringResource(R.string.push_gateway_invalid)) }
                    } else {
                        null
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            if (current.isNotEmpty()) {
                TextButton(onClick = { onSave("") }) { Text(stringResource(R.string.push_gateway_use_automatic)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    )
}

private const val PUBLIC_GATEWAY_HOST = "matrix.gateway.unifiedpush.org"
