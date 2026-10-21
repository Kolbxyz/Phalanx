package org.phalanx.android.ui.screens.roomlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.data.model.RoomSummary
import org.phalanx.android.ui.components.InitialAvatar
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun RoomRow(room: RoomSummary, shape: Shape, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier, shape = shape, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoomAvatar(room)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = room.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                room.preview?.let { preview ->
                    Text(
                        text = if (room.previewIsOwn) stringResource(R.string.roomlist_you_prefix, preview) else preview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                room.lastActivityMillis?.let { millis ->
                    Text(
                        text = remember(millis) { formatActivity(millis) },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (room.unreadCount > 0) UnreadBadge(room.unreadCount, mention = room.highlightCount > 0)
            }
        }
    }
}

/** A cookie for a person, a flower for a group: the shape alone says which kind of chat this is. */
@Composable
private fun RoomAvatar(room: RoomSummary) {
    val shape = if (room.isDirect) MaterialShapes.Cookie9Sided.toShape() else MaterialShapes.Cookie4Sided.toShape()
    val container = if (room.isDirect) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer
    val content = if (room.isDirect) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
    InitialAvatar(name = room.name, shape = shape, size = 48.dp, container = container, content = content)
}

@Composable
private fun UnreadBadge(count: Long, mention: Boolean) {
    val description = pluralStringResource(R.plurals.roomlist_unread, count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), count)
    Surface(
        shape = CircleShape,
        color = if (mention) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        contentColor = if (mention) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier
            .defaultMinSize(minWidth = 24.dp, minHeight = 24.dp)
            .semantics { contentDescription = description }
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 7.dp)) {
            Text(text = if (count > MAX_BADGE) "$MAX_BADGE+" else count.toString(), style = MaterialTheme.typography.labelSmall)
        }
    }
}

private const val MAX_BADGE = 99L

/** Today shows the time; anything older shows the date. */
private fun formatActivity(millis: Long): String {
    val zone = ZoneId.systemDefault()
    val moment = Instant.ofEpochMilli(millis).atZone(zone)
    val formatter = if (moment.toLocalDate() == LocalDate.now(zone)) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    } else {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
    }
    return formatter.format(moment)
}
