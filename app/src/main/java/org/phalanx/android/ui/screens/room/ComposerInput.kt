package org.phalanx.android.ui.screens.room

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import org.phalanx.android.R

private const val MAX_LINES = 6
private val ButtonSize = 48.dp

/** Attach, the text, then send, which is a microphone while there is nothing to send. */
@Composable
internal fun InputRow(state: ComposerState, actions: ComposerActions, enterToSend: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        // Centred on the field's single-line height, and stays at the bottom as the field grows.
        Box(Modifier.height(TextFieldHeight), contentAlignment = Alignment.Center) {
            FilledTonalIconButton(
                onClick = actions.onAttach,
                enabled = state.mode !is ComposerMode.Edit,
                modifier = Modifier.size(ButtonSize)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.composer_attach))
            }
        }
        TextField(
            value = state.draft,
            onValueChange = actions.onDraftChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.composer_hint)) },
            shape = RoundedCornerShape(24.dp),
            maxLines = MAX_LINES,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = if (enterToSend) ImeAction.Send else ImeAction.Default
            ),
            keyboardActions = KeyboardActions(onSend = { if (state.canSend) actions.onSend() }),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            )
        )
        Box(Modifier.height(TextFieldHeight), contentAlignment = Alignment.Center) {
            SendOrRecord(state, actions)
        }
    }
}

@Composable
private fun SendOrRecord(state: ComposerState, actions: ComposerActions) {
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = state.showsMic,
        transitionSpec = {
            (scaleIn(motion.fastSpatialSpec()) + fadeIn(motion.fastEffectsSpec())) togetherWith
                (scaleOut(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()))
        }
    ) { mic ->
        if (mic) {
            FilledTonalIconButton(onClick = actions.onRecord, modifier = Modifier.size(ButtonSize)) {
                Icon(Icons.Rounded.Mic, contentDescription = stringResource(R.string.composer_record))
            }
        } else {
            val editing = state.mode is ComposerMode.Edit
            SendButton(
                editing = editing,
                enabled = state.canSend,
                onSend = actions.onSend,
                // Only text can wait: attachments and edits go now.
                onLongPress = actions.onSchedule.takeIf { !editing && state.attachments.isEmpty() }
            )
        }
    }
}

/** A filled round button; a long press, where offered, opens scheduling with a haptic tick. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SendButton(editing: Boolean, enabled: Boolean, onSend: () -> Unit, onLongPress: (() -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val scheduleLabel = stringResource(R.string.schedule_action)
    Surface(
        shape = CircleShape,
        color = if (enabled) colors.primary else colors.onSurface.copy(alpha = DISABLED_ALPHA),
        contentColor = if (enabled) colors.onPrimary else colors.onSurface.copy(alpha = DISABLED_CONTENT_ALPHA),
        modifier = Modifier
            .size(ButtonSize)
            .clip(CircleShape)
            .combinedClickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onSend,
                onLongClickLabel = if (onLongPress != null) scheduleLabel else null,
                onLongClick = onLongPress?.let { open ->
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        open()
                    }
                }
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                if (editing) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.Send,
                contentDescription = stringResource(
                    if (editing) R.string.composer_save_edit else R.string.composer_send
                )
            )
        }
    }
}

private const val DISABLED_ALPHA = 0.12f
private const val DISABLED_CONTENT_ALPHA = 0.38f

// TextField's minimum height in Material 3, so the side buttons line up with a one-line field.
private val TextFieldHeight = 56.dp
