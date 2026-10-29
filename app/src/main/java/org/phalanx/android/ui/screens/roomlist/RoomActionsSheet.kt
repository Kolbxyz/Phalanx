package org.phalanx.android.ui.screens.roomlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.data.model.RoomSummary
import org.phalanx.android.ui.components.MatrixAvatar
import org.phalanx.android.ui.components.MenuAction
import org.phalanx.android.ui.components.MenuActions
import org.phalanx.android.ui.components.ModalSheet
import org.phalanx.android.ui.theme.Symbols

/** A long press on a chat in the list: the room's name, then what can be done without opening it. */
@Composable
internal fun RoomActionsSheet(
    room: RoomSummary,
    onDismiss: () -> Unit,
    onToggleRead: () -> Unit,
    onToggleFavourite: () -> Unit,
    onToggleArchived: () -> Unit,
    onToggleMuted: () -> Unit,
    onDetails: () -> Unit,
    onLeave: () -> Unit
) {
    ModalSheet(onDismissRequest = onDismiss) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MatrixAvatar(room.name, room.avatar, 40.dp)
            Text(room.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(8.dp))
        MenuActions(
            actions = listOf(
                if (room.hasUnread) {
                    MenuAction(Symbols.MarkChatRead, R.string.menu_mark_read, onToggleRead)
                } else {
                    MenuAction(Symbols.MarkChatUnread, R.string.menu_mark_unread, onToggleRead)
                },
                // Toggles keep one short label: whether they are on shows in the button itself.
                MenuAction(
                    if (room.isFavourite) Symbols.Star else Symbols.StarOutline,
                    R.string.menu_favorite,
                    onToggleFavourite,
                    room.isFavourite
                ),
                MenuAction(
                    if (room.isMuted) Symbols.NotificationsOff else Symbols.Notifications,
                    R.string.menu_mute,
                    onToggleMuted,
                    room.isMuted
                ),
                MenuAction(Symbols.Archive, R.string.menu_archive, onToggleArchived, room.isLowPriority),
                MenuAction(Symbols.Info, R.string.room_action_details, onDetails)
            ),
            danger = listOf(MenuAction(Symbols.Logout, R.string.room_action_leave, onLeave))
        )
        Spacer(Modifier.height(8.dp))
    }
}

/** Leaving can't be undone from here: an invite-only room needs a new invite to come back. */
@Composable
internal fun LeaveRoomDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Symbols.Logout, contentDescription = null) },
        title = { Text(stringResource(R.string.leave_title, name)) },
        text = { Text(stringResource(R.string.leave_body)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.room_action_leave)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
