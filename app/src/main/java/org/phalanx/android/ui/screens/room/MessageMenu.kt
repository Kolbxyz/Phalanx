package org.phalanx.android.ui.screens.room

import android.content.ClipData
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.model.TimelineEntry

/** The long-press sheet for [message], and the delete confirmation it can lead to. */
@Composable
internal fun MessageMenu(message: TimelineEntry.Message?, onDismiss: () -> Unit, viewModel: RoomViewModel) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var deleting by remember { mutableStateOf<TimelineEntry.Message?>(null) }
    var blocking by remember { mutableStateOf<TimelineEntry.Message?>(null) }
    var forwarding by remember { mutableStateOf<TimelineEntry.Message?>(null) }
    val muted by viewModel.muted.collectAsStateWithLifecycle()
    val text = (message?.body as? MessageBody.Text)?.text

    // Every choice closes the sheet once it has acted.
    fun then(action: () -> Unit): () -> Unit = {
        action()
        onDismiss()
    }
    if (message != null) {
        MessageActionsSheet(
            message = message,
            onDismiss = onDismiss,
            onReact = { key -> then { viewModel.toggleReaction(message, key) }() },
            onReply = then { viewModel.composer.reply(message) },
            onEdit = then { text?.let { viewModel.composer.edit(message, it) } },
            onCopy = then {
                text?.let { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, it))) } }
            },
            onForward = then { forwarding = message },
            onDelete = then { deleting = message },
            senderMuted = message.senderId in muted,
            onToggleMute = then { viewModel.setMuted(message.senderId, message.senderId !in muted) },
            onBlock = then { blocking = message }
        )
    }
    forwarding?.let { target ->
        val rooms by viewModel.rooms.collectAsStateWithLifecycle()
        ForwardSheet(
            rooms = rooms?.filter { it.id != viewModel.roomId },
            onPick = { roomId ->
                viewModel.forward(target, roomId)
                forwarding = null
            },
            onDismiss = { forwarding = null }
        )
    }
    deleting?.let { target ->
        DeleteMessageDialog(
            onConfirm = {
                viewModel.redact(target)
                deleting = null
            },
            onDismiss = { deleting = null }
        )
    }
    blocking?.let { target ->
        BlockUserDialog(
            name = target.senderName,
            onConfirm = {
                viewModel.block(target.senderId)
                blocking = null
            },
            onDismiss = { blocking = null }
        )
    }
}
