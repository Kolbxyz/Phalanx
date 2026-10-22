package org.phalanx.android.ui.screens.roomlist

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.phalanx.android.R
import org.phalanx.android.data.model.RoomSummary
import org.phalanx.android.ui.screens.room.summaryOf

/** The line under a room's name: this account's draft if it left one, else the room's last message. */
@Composable
internal fun RowPreview(room: RoomSummary, draft: String?, unread: Boolean) {
    val colors = MaterialTheme.colorScheme
    if (draft != null) {
        // What this account left unsent outranks what the room last said.
        Text(
            text = stringResource(R.string.roomlist_draft_prefix, draft),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    } else {
        room.preview?.let { body ->
            val preview = summaryOf(body)
            val sender = room.previewSender
            Text(
                text = when {
                    room.previewIsOwn -> stringResource(R.string.roomlist_you_prefix, preview)
                    !room.isDirect && sender != null ->
                        stringResource(R.string.roomlist_sender_prefix, sender, preview)
                    else -> preview
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (unread) FontWeight.SemiBold else null,
                color = if (unread) colors.onSurface else colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
