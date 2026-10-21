package org.phalanx.android.ui.screens.roomlist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.data.model.RoomSummary
import org.phalanx.android.ui.components.MatrixAvatar
import org.phalanx.android.ui.screens.room.summaryOf
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** One chat. Unread ones stand out in weight and colour; a long press opens its menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RoomRow(
    room: RoomSummary,
    shape: Shape,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val unread = room.hasUnread
    Surface(
        modifier = modifier,
        shape = shape,
        color = if (unread) colors.surfaceContainerHighest else colors.surfaceContainer
    ) {
        Row(
            modifier = Modifier
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoomAvatar(room)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = room.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (unread) FontWeight.ExtraBold else FontWeight.SemiBold,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (room.isFavourite) {
                        Icon(
                            Icons.Rounded.Star,
                            contentDescription = stringResource(R.string.roomlist_favourite),
                            tint = colors.tertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
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
                        fontWeight = if (unread) FontWeight.Medium else null,
                        color = if (unread) colors.onSurface else colors.onSurfaceVariant,
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
                        fontWeight = if (unread) FontWeight.Bold else null,
                        color = if (unread) colors.primary else colors.onSurfaceVariant
                    )
                }
                when {
                    room.unreadCount > 0 -> UnreadBadge(room.unreadCount, mention = room.highlightCount > 0)
                    room.isMarkedUnread -> UnreadDot()
                }
            }
        }
    }
}

@Composable
private fun UnreadDot() {
    val description = stringResource(R.string.roomlist_marked_unread)
    Box(
        Modifier
            .size(12.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape)
            .semantics { contentDescription = description }
    )
}

/** A cookie for a person, a flower for a group: the shape alone says which kind of chat this is. */
@Composable
private fun RoomAvatar(room: RoomSummary) {
    val shape = if (room.isDirect) MaterialShapes.Cookie9Sided.toShape() else MaterialShapes.Cookie4Sided.toShape()
    val colors = MaterialTheme.colorScheme
    val container = if (room.isDirect) colors.tertiaryContainer else colors.primaryContainer
    val content = if (room.isDirect) colors.onTertiaryContainer else colors.onPrimaryContainer
    MatrixAvatar(
        name = room.name,
        media = room.avatar,
        size = 56.dp,
        shape = shape,
        container = container,
        content = content,
        textStyle = MaterialTheme.typography.titleLarge
    )
}

@Composable
private fun UnreadBadge(count: Long, mention: Boolean) {
    val quantity = count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    val description = pluralStringResource(R.plurals.roomlist_unread, quantity, count)
    Surface(
        shape = CircleShape,
        color = if (mention) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        contentColor = if (mention) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier
            .defaultMinSize(minWidth = 24.dp, minHeight = 24.dp)
            .semantics { contentDescription = description }
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 7.dp)) {
            Text(
                text = if (count > MAX_BADGE) "$MAX_BADGE+" else count.toString(),
                style = MaterialTheme.typography.labelSmall
            )
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
