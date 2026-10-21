package org.phalanx.android.ui.screens.settings

import android.content.ClipData
import android.os.PersistableBundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.PhonelinkLock
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.phalanx.android.R
import org.phalanx.android.data.repository.VerificationStep

/** The recovery key, shown once: the user must save it somewhere safe before closing. */
@Composable
internal fun RecoveryKeyDialog(key: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(Icons.Rounded.Key, contentDescription = null) },
        title = { Text(stringResource(R.string.security_key_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.security_key_body))
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    SelectionContainer {
                        Text(
                            text = key,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                TextButton(onClick = {
                    // Marked sensitive, so the clipboard preview and keyboard suggestions hide it.
                    val clip = ClipData.newPlainText(null, key).apply {
                        description.extras = PersistableBundle().apply {
                            putBoolean("android.content.extra.IS_SENSITIVE", true)
                        }
                    }
                    scope.launch { clipboard.setClipEntry(ClipEntry(clip)) }
                }) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.account_copy), modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.security_key_saved)) } }
    )
}

@Composable
internal fun EnterKeyDialog(busy: Boolean, rejected: Boolean, onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var key by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Key, contentDescription = null) },
        title = { Text(stringResource(R.string.security_enter_key)) },
        text = {
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text(stringResource(R.string.security_key_label)) },
                isError = rejected,
                supportingText = if (rejected) {
                    { Text(stringResource(R.string.security_key_rejected)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                enabled = !busy
            )
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(key) }, enabled = key.isNotBlank() && !busy) {
                if (busy) LoadingIndicator(Modifier.size(20.dp)) else Text(stringResource(R.string.security_unlock))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
internal fun FailedDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.ErrorOutline, contentDescription = null) },
        text = { Text(stringResource(R.string.timeline_action_failed)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } }
    )
}

/** Compare the emoji on both devices; matching ones verify this session. */
@Composable
internal fun VerificationDialog(
    step: VerificationStep,
    onMatch: () -> Unit,
    onNoMatch: () -> Unit,
    onClose: () -> Unit
) {
    val finished = step == VerificationStep.Done || step == VerificationStep.Failed
    AlertDialog(
        onDismissRequest = {},
        icon = {
            Icon(
                when (step) {
                    VerificationStep.Done -> Icons.Rounded.VerifiedUser
                    VerificationStep.Failed -> Icons.Rounded.ErrorOutline
                    else -> Icons.Rounded.PhonelinkLock
                },
                contentDescription = null
            )
        },
        title = { Text(stringResource(R.string.security_verify_device)) },
        text = {
            when (step) {
                VerificationStep.Requested -> Text(stringResource(R.string.security_verify_waiting))
                VerificationStep.Starting -> LoadingIndicator(Modifier.size(48.dp))
                is VerificationStep.CompareEmoji -> EmojiGrid(step.emoji)
                VerificationStep.Done -> Text(stringResource(R.string.security_verify_done))
                VerificationStep.Failed -> Text(stringResource(R.string.security_verify_failed))
                VerificationStep.Idle -> Unit
            }
        },
        confirmButton = {
            when {
                step is VerificationStep.CompareEmoji -> TextButton(onClick = onMatch) {
                    Text(stringResource(R.string.security_emoji_match))
                }
                finished -> TextButton(onClick = onClose) { Text(stringResource(R.string.ok)) }
            }
        },
        dismissButton = {
            if (!finished) {
                TextButton(onClick = onNoMatch) {
                    Text(
                        stringResource(
                            when (step) {
                                is VerificationStep.CompareEmoji -> R.string.security_emoji_no_match
                                else -> R.string.cancel
                            }
                        )
                    )
                }
            }
        }
    )
}

@Composable
private fun EmojiGrid(emoji: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.security_emoji_compare))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            emoji.forEach { (symbol, name) ->
                Column(Modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(symbol, style = MaterialTheme.typography.headlineMedium)
                    Text(name, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
