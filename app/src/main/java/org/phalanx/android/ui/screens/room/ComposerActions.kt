package org.phalanx.android.ui.screens.room

import androidx.compose.runtime.Stable
import androidx.compose.ui.text.input.TextFieldValue

/** What the composer asks of the screen; built once per screen. */
@Stable
internal class ComposerActions(
    val onDraftChange: (TextFieldValue) -> Unit,
    val onPickSuggestion: (MentionSuggestion) -> Unit,
    val onSend: () -> Unit,
    val onCancelMode: () -> Unit,
    val onAttach: () -> Unit,
    val onRemoveAttachment: (String) -> Unit,
    /** Opens the editor on a picture waiting to be sent. */
    val onEditAttachment: (String) -> Unit,
    val onRecord: () -> Unit,
    val onCancelRecording: () -> Unit,
    val onSendRecording: () -> Unit,
    /** Long press on send: pick a time instead of sending now. */
    val onSchedule: () -> Unit,
    val onSendScheduledNow: (String) -> Unit,
    val onCancelScheduled: (String) -> Unit
)
