package org.phalanx.android.ui.screens.roomlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.data.model.Bridge
import org.phalanx.android.data.model.RoomSummary
import org.phalanx.android.ui.components.MatrixAvatar
import org.phalanx.android.ui.theme.Symbols

/** One chat. Unread ones stand out in weight and colour; a long press opens its menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RoomRow(
    room: RoomSummary,
    draft: String?,
    shape: Shape,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    networks: List<Bridge> = emptyList(),
    /** Picked up into its menu: drawn as a gap until the menu closes. */
    lifted: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    val unread = room.hasUnread
    val interaction = remember { MutableInteractionSource() }
    val container by animateColorAsState(
        if (unread) colors.surfaceContainerHighest else colors.surfaceContainer,
        MaterialTheme.motionScheme.defaultEffectsSpec()
    )
    val presence by animateFloatAsState(if (lifted) 0f else 1f, MaterialTheme.motionScheme.fastEffectsSpec())
    Surface(
        modifier = modifier.pressSink(interaction).graphicsLayer { alpha = presence },
        shape = shape,
        color = container
    ) {
        Row(
            modifier = Modifier
                .combinedClickable(
                    interactionSource = interaction,
                    indication = ripple(),
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                RoomAvatar(room)
                NetworkBadges(
                    networks,
                    ring = container,
                    modifier = Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp)
                )
            }
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
                    NameMarks(room)
                }
                RowPreview(room, draft, unread)
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
                val motion = MaterialTheme.motionScheme
                AnimatedVisibility(
                    visible = room.unreadCount > 0 || room.isMarkedUnread,
                    enter = scaleIn(motion.fastSpatialSpec()) + fadeIn(motion.fastEffectsSpec()),
                    exit = scaleOut(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec())
                ) {
                    if (room.unreadCount > 0) {
                        UnreadBadge(room.unreadCount, mention = room.highlightCount > 0, muted = room.isMuted)
                    } else {
                        UnreadDot()
                    }
                }
            }
        }
    }
}

/** Small marks after the name: muted, then favourite. */
@Composable
private fun NameMarks(room: RoomSummary) {
    if (room.isMuted) {
        Icon(
            Symbols.NotificationsOff,
            contentDescription = stringResource(R.string.roomlist_muted),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
    if (room.isFavourite) {
        Icon(
            Symbols.Star,
            contentDescription = stringResource(R.string.roomlist_favourite),
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(16.dp)
        )
    }
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
