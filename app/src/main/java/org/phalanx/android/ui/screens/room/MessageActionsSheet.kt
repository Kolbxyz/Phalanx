package org.phalanx.android.ui.screens.room

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.model.TimelineEntry

private val QuickReactions = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")

/** What a long press on a message offers: a quick reaction, then the actions that apply to it. */
@Composable
internal fun MessageActionsSheet(
    message: TimelineEntry.Message,
    onDismiss: () -> Unit,
    onReact: (String) -> Unit,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss) {
        val live = message.body != MessageBody.Redacted
        if (live && message.eventId != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                QuickReactions.forEach { key ->
                    val mine = message.reactions.any { it.key == key && it.byMe }
                    Surface(
                        onClick = { onReact(key) },
                        shape = MaterialShapes.Cookie6Sided.toShape(),
                        color = if (mine) colors.secondaryContainer else colors.surfaceContainerHigh,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(key, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
        }
        if (message.canReply) Action(Icons.AutoMirrored.Rounded.Reply, R.string.action_reply, onReply)
        if (message.body is MessageBody.Text) Action(Icons.Rounded.ContentCopy, R.string.action_copy, onCopy)
        if (message.canEdit && message.body is MessageBody.Text) {
            Action(Icons.Rounded.Edit, R.string.action_edit, onEdit)
        }
        if (message.isOwn && live) Action(Icons.Rounded.Delete, R.string.action_delete, onDelete, destructive = true)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Action(icon: ImageVector, @StringRes label: Int, onClick: () -> Unit, destructive: Boolean = false) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    ListItem(
        headlineContent = { Text(stringResource(label)) },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(headlineColor = color, leadingIconColor = color),
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick)
    )
}

@Composable
internal fun DeleteMessageDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
        title = { Text(stringResource(R.string.delete_title)) },
        text = { Text(stringResource(R.string.delete_body)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
