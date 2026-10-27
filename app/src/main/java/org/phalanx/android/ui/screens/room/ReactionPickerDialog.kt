package org.phalanx.android.ui.screens.room

import android.icu.lang.UCharacter
import android.icu.lang.UProperty
import android.icu.text.BreakIterator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import org.phalanx.android.R

/**
 * Reacts with any emoji, not just the quick ones: the system keyboard's own emoji panel does the
 * picking, and the reaction goes out as soon as one emoji has been typed.
 */
@Composable
internal fun ReactionPickerDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reaction_pick_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { input -> if (input.isSingleEmoji()) onPick(input) else text = input },
                isError = text.isNotEmpty(),
                supportingText = {
                    val hint = if (text.isEmpty()) R.string.reaction_pick_hint else R.string.reaction_pick_invalid
                    Text(stringResource(hint))
                },
                singleLine = true,
                modifier = Modifier.focusRequester(focus)
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/** One user-perceived character that is an emoji; plain ASCII (digits included) is never one. */
internal fun String.isSingleEmoji(): Boolean {
    if (isEmpty() || all { it.code < ASCII_LIMIT }) return false
    if (codePoints().noneMatch { UCharacter.hasBinaryProperty(it, UProperty.EMOJI) }) return false
    val breaks = BreakIterator.getCharacterInstance()
    breaks.setText(this)
    return breaks.next() == length
}

private const val ASCII_LIMIT = 0x80
