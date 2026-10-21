package org.phalanx.android.ui.screens.room

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.ui.components.MatrixAvatar

private val LargeRadius = 24.dp
private val SeamRadius = 6.dp
private val AvatarSize = 32.dp
private val MaxBubbleWidth = 320.dp
private const val PRESSED_SCALE = 0.96f

/**
 * One message. Consecutive messages from one sender form a run, shaped like a segmented list: the
 * run's outer corners are round and the seams on the sender's side tight. Only the first message
 * of a run shows the sender's name, and only the last the avatar.
 */
@Composable
internal fun MessageBubble(
    message: TimelineEntry.Message,
    isGroupChat: Boolean,
    startsRun: Boolean,
    endsRun: Boolean,
    actions: MessageActions,
    modifier: Modifier = Modifier
) {
    val own = message.isOwn
    val showAvatar = isGroupChat && !own
    Row(
        modifier = modifier.fillMaxWidth().padding(top = if (startsRun) 10.dp else 0.dp),
        horizontalArrangement = if (own) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (showAvatar) {
            if (endsRun) {
                MatrixAvatar(message.senderName, message.senderAvatar, AvatarSize)
            } else {
                Spacer(Modifier.width(AvatarSize))
            }
            Spacer(Modifier.width(8.dp))
        }
        Column(horizontalAlignment = if (own) Alignment.End else Alignment.Start) {
            if (showAvatar && startsRun) {
                Text(
                    text = message.senderName,
                    style = MaterialTheme.typography.labelLarge,
                    color = senderColor(message.senderId),
                    maxLines = 1,
                    modifier = Modifier.padding(start = 14.dp, bottom = 4.dp)
                )
            }
            SwipeToReply(enabled = message.canReply, onReply = { actions.onReply(message) }) {
                Bubble(message, startsRun, endsRun, actions)
            }
            if (message.reactions.isNotEmpty()) {
                ReactionRow(
                    reactions = message.reactions,
                    onToggle = { actions.onToggleReaction(message, it) },
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(message: TimelineEntry.Message, startsRun: Boolean, endsRun: Boolean, actions: MessageActions) {
    val own = message.isOwn
    val top = if (startsRun) LargeRadius else SeamRadius
    val bottom = if (endsRun) LargeRadius else SeamRadius
    val shape = RoundedCornerShape(
        topStart = if (own) LargeRadius else top,
        topEnd = if (own) top else LargeRadius,
        bottomEnd = if (own) bottom else LargeRadius,
        bottomStart = if (own) LargeRadius else bottom
    )
    val layout = bubbleLayout(message)
    val colors = MaterialTheme.colorScheme

    // Pressing sinks the bubble a little, the Expressive cue that a long press will act on it.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) PRESSED_SCALE else 1f, MaterialTheme.motionScheme.fastSpatialSpec())
    val haptics = LocalHapticFeedback.current

    Surface(
        shape = shape,
        color = when {
            layout == BubbleLayout.Bare -> Color.Transparent
            own -> colors.primary
            else -> colors.surfaceContainerHigh
        },
        contentColor = when {
            layout == BubbleLayout.Bare -> colors.onSurface
            own -> colors.onPrimary
            else -> colors.onSurface
        },
        modifier = Modifier
            .widthIn(max = MaxBubbleWidth)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = {},
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    actions.onLongPress(message)
                }
            )
    ) {
        BubbleContent(message, layout, actions)
    }
}

/** A stable colour per sender from the theme's accent roles, so names stay legible in any palette. */
@Composable
private fun senderColor(senderId: String): Color {
    val colors = MaterialTheme.colorScheme
    val palette = listOf(colors.primary, colors.tertiary, colors.secondary)
    return palette[Math.floorMod(senderId.hashCode(), palette.size)]
}
