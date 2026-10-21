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
    val onSendRecording: () -> Unit,
    /** Long press on send: pick a time instead of sending now. */
    val onSchedule: () -> Unit,
    val onSendScheduledNow: (String) -> Unit,
    val onCancelScheduled: (String) -> Unit
)
