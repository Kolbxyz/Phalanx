package org.phalanx.android.ui.screens.room

import androidx.compose.runtime.Stable

/** What the composer asks of the screen; built once per screen. */
@Stable
internal class ComposerActions(
    val onDraftChange: (String) -> Unit,
    val onSend: () -> Unit,
    val onCancelMode: () -> Unit,
    val onAttach: () -> Unit,
    val onRemoveAttachment: (String) -> Unit,
    val onRecord: () -> Unit,
    val onCancelRecording: () -> Unit,
    val onSendRecording: () -> Unit
)
