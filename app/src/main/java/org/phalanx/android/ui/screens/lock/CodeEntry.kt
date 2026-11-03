package org.phalanx.android.ui.screens.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.core.security.CodeHash

private val FieldWidth = 280.dp

/** A code field under the unlock button. It reads like any app code; see [LockViewModel]. */
@Composable
internal fun CodeEntry(check: CodeCheck, onSubmit: (String) -> Unit, onEdited: () -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    val checking = check == CodeCheck.Checking
    val submit = { onSubmit(code) }
    Column(
        modifier = Modifier.widthIn(max = FieldWidth),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(
            value = code,
            onValueChange = { typed ->
                code = typed.filter(Char::isDigit).take(CodeHash.MAX_LENGTH)
                onEdited()
            },
            label = { Text(stringResource(R.string.lock_code_label)) },
            isError = check == CodeCheck.Wrong,
            supportingText = if (check == CodeCheck.Wrong) {
                { Text(stringResource(R.string.lock_code_wrong)) }
            } else {
                null
            },
            singleLine = true,
            enabled = !checking,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth()
        )
        if (checking) {
            LoadingIndicator()
        } else {
            FilledTonalButton(onClick = submit, enabled = code.isNotEmpty()) {
                Text(stringResource(R.string.lock_code_submit))
            }
        }
    }
}
