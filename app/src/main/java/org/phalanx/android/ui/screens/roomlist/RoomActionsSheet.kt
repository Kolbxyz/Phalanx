package org.phalanx.android.ui.screens.roomlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.data.model.RoomSummary
import org.phalanx.android.ui.components.InlineConfirm
import org.phalanx.android.ui.components.MenuAction
import org.phalanx.android.ui.components.MenuActions
import org.phalanx.android.ui.theme.Symbols

/**
 * What a long press on a chat offers, under its peek. Read, favourite and mute flip in place and
 * the menu stays, so several can be set in one go; archiving files the chat away and closes it.
 */
@Composable
internal fun RoomActions(
    room: RoomSummary,
    onToggleRead: () -> Unit,
    onToggleFavourite: () -> Unit,
    onToggleMuted: () -> Unit,
    onArchive: () -> Unit,
    onDetails: () -> Unit,
    onLeave: () -> Unit
) {
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
            if (room.isLowPriority) {
                MenuAction(Symbols.Unarchive, R.string.menu_unarchive, onArchive)
            } else {
                MenuAction(Symbols.Archive, R.string.menu_archive, onArchive)
            }
        ),
        extra = { DetailsRow(onDetails) },
        footer = {
            InlineConfirm(
                icon = Symbols.Logout,
                label = stringResource(R.string.room_action_leave),
                question = stringResource(R.string.leave_title, room.name),
                body = stringResource(R.string.leave_body),
                confirmLabel = stringResource(R.string.room_action_leave),
                onConfirm = onLeave
            )
        }
    )
}

@Composable
private fun DetailsRow(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = colors.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Icon(Symbols.Info, contentDescription = null)
            Text(
                stringResource(R.string.room_action_details),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Icon(Symbols.KeyboardArrowRight, contentDescription = null, tint = colors.onSurfaceVariant)
        }
    }
}
