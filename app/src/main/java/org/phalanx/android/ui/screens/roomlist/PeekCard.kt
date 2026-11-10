package org.phalanx.android.ui.screens.roomlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.data.model.RoomSummary
import org.phalanx.android.ui.components.MatrixAvatar
import org.phalanx.android.ui.components.riseIn
import org.phalanx.android.ui.components.stagger
import org.phalanx.android.ui.screens.people.sourceOf
import org.phalanx.android.ui.screens.room.summaryOf
import org.phalanx.android.ui.theme.Symbols

/**
 * A look into the chat a menu is open on, floating above the dimmed list: its last few messages,
 * and a reminder that looking here leaves them unread.
 */
@Composable
internal fun PeekCard(room: RoomSummary, lines: List<PeekLine>) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = colors.surfaceContainerLow,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 14.dp, bottom = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                MatrixAvatar(
                    name = room.name,
                    media = room.avatar,
                    size = 40.dp,
                    shape = (if (room.isDirect) MaterialShapes.Cookie9Sided else MaterialShapes.Cookie4Sided).toShape(),
                    container = if (room.isDirect) colors.tertiaryContainer else colors.primaryContainer,
                    content = if (room.isDirect) colors.onTertiaryContainer else colors.onPrimaryContainer
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        room.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(subtitle(room), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                }
            }
            Surface(shape = RoundedCornerShape(24.dp), color = colors.surfaceContainer) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Icon(Symbols.VisibilityOff, contentDescription = null, modifier = Modifier.size(14.dp))
                        Text(
                            stringResource(R.string.peek_caption),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onSurfaceVariant
                        )
                    }
                    lines.forEachIndexed { index, line ->
                        PeekBubble(
                            line,
                            showSender = !room.isDirect,
                            Modifier.riseIn(stagger(index, PEEK_FIRST, PEEK_STEP))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PeekBubble(line: PeekLine, showSender: Boolean, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val own = line.sender == null
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (own) Arrangement.End else Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (own) colors.primary else colors.surfaceContainerHighest,
            contentColor = if (own) colors.onPrimary else colors.onSurface,
            modifier = modifier.widthIn(max = 280.dp)
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                if (showSender && line.sender != null) {
                    Text(
                        line.sender,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary
                    )
                }
                Text(
                    summaryOf(line.body),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** "{network} · {n} unread", or "· marked unread" for a chat marked by hand. */
@Composable
private fun subtitle(room: RoomSummary): String {
    val network = stringResource(sourceOf(room).label)
    val unread = room.unreadCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    return when {
        unread > 0 -> stringResource(
            R.string.catch_up_card_meta,
            network,
            pluralStringResource(R.plurals.catch_up_unread, unread, unread)
        )
        room.isMarkedUnread -> stringResource(
            R.string.catch_up_card_meta,
            network,
            stringResource(R.string.peek_marked_unread)
        )
        else -> network
    }
}

private const val PEEK_FIRST = 160L
private const val PEEK_STEP = 70L
