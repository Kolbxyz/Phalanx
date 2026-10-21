package org.phalanx.android.ui.screens.roomlist

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MarkChatRead
import androidx.compose.material.icons.rounded.MarkChatUnread
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.data.model.RoomSummary
import org.phalanx.android.ui.components.MatrixAvatar

/** A long press on a chat in the list: the room's name, then what can be done without opening it. */
@Composable
internal fun RoomActionsSheet(
    room: RoomSummary,
    onDismiss: () -> Unit,
    onToggleRead: () -> Unit,
    onToggleFavourite: () -> Unit,
    onDetails: () -> Unit,
    onLeave: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MatrixAvatar(room.name, room.avatar, 40.dp)
            Text(room.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(8.dp))
        if (room.hasUnread) {
            Action(Icons.Rounded.MarkChatRead, R.string.room_action_mark_read, onToggleRead)
        } else {
            Action(Icons.Rounded.MarkChatUnread, R.string.room_action_mark_unread, onToggleRead)
        }
        if (room.isFavourite) {
            Action(Icons.Rounded.StarOutline, R.string.room_action_unfavourite, onToggleFavourite)
        } else {
            Action(Icons.Rounded.Star, R.string.room_action_favourite, onToggleFavourite)
        }
        Action(Icons.Rounded.Info, R.string.room_action_details, onDetails)
        Action(Icons.AutoMirrored.Rounded.Logout, R.string.room_action_leave, onLeave, destructive = true)
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

/** Leaving can't be undone from here: an invite-only room needs a new invite to come back. */
@Composable
internal fun LeaveRoomDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null) },
        title = { Text(stringResource(R.string.leave_title, name)) },
        text = { Text(stringResource(R.string.leave_body)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.room_action_leave)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
