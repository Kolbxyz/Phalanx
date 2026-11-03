package org.phalanx.android.ui.screens.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.phalanx.android.R
import org.phalanx.android.core.security.CodeHash
import org.phalanx.android.ui.components.SegmentedRow
import org.phalanx.android.ui.components.segmentedShape
import org.phalanx.android.ui.theme.Symbols

/** The duress code row, shown under app lock, which it needs; its dialog sets or removes the code. */
@Composable
internal fun DuressSetting(viewModel: DuressViewModel = hiltViewModel()) {
    val isSet by viewModel.isSet.collectAsStateWithLifecycle()
    val failed by viewModel.failed.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    SegmentedRow(
        headline = stringResource(R.string.settings_duress),
        supporting = stringResource(if (isSet) R.string.settings_duress_on else R.string.settings_duress_off),
        shape = segmentedShape(0, 1),
        leading = { Icon(Symbols.Password, contentDescription = null) },
        interaction = Modifier.clickable { editing = true }
    )
    if (editing) {
        val close = {
            editing = false
            viewModel.dismissed()
        }
        DuressDialog(
            isSet = isSet,
            failed = failed,
            onSave = { viewModel.save(it, close) },
            onRemove = {
                viewModel.remove()
                close()
            },
            onDismiss = close
        )
    }
}

@Composable
private fun DuressDialog(
    isSet: Boolean,
    failed: Boolean,
    onSave: (String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    val mismatch = again.isNotEmpty() && again != code
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Symbols.Password, contentDescription = null) },
        title = { Text(stringResource(R.string.duress_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.duress_body))
                CodeField(code, R.string.duress_code) { code = it }
                CodeField(again, R.string.duress_code_again, isError = mismatch) { again = it }
                val note = when {
                    failed -> R.string.duress_save_failed
                    mismatch -> R.string.duress_mismatch
                    else -> R.string.duress_rules
                }
                val colors = MaterialTheme.colorScheme
                Text(
                    text = stringResource(note),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (failed || mismatch) colors.error else colors.onSurfaceVariant
                )
                if (isSet) TextButton(onClick = onRemove) { Text(stringResource(R.string.duress_remove)) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(code) }, enabled = CodeHash.isValid(code) && again == code) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun CodeField(value: String, @StringRes label: Int, isError: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(CodeHash.MAX_LENGTH)) },
        label = { Text(stringResource(label)) },
        isError = isError,
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
    )
}
