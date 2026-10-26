package org.phalanx.android.ui.screens.room

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.phalanx.android.R
import org.phalanx.android.data.model.SendState
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.ui.components.MatrixAvatar
import org.phalanx.android.ui.theme.LocalReducedMotion
import org.phalanx.android.ui.theme.Symbols

private val LargeRadius = 24.dp
private val SeamRadius = 6.dp
private val MediaRadius = 14.dp
private val AvatarSize = 32.dp
private val MaxBubbleWidth = 320.dp
private val ReactionOverlap = 6.dp
private const val PRESSED_SCALE = 0.96f
private const val APPEAR_SCALE = 0.9f
private val APPEAR_RISE = 48.dp

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
    isNew: Boolean,
    actions: MessageActions,
    modifier: Modifier = Modifier
) {
    val own = message.isOwn
    val showAvatar = isGroupChat && !own
    val time = remember(message.timestampMillis) { formatTime(message.timestampMillis) }
    MessageSwipe(
        time = time,
        canReply = message.canReply,
        onReply = { actions.onReply(message) },
        modifier = modifier.padding(top = if (startsRun) 10.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().appear(isNew, own, message.sendState == SendState.Sent),
            horizontalArrangement = if (own) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom
        ) {
            if (showAvatar) {
                if (endsRun) {
                    MatrixAvatar(
                        name = message.senderName,
                        media = message.senderAvatar,
                        size = AvatarSize,
                        modifier = Modifier.clickable(role = Role.Button) { actions.onOpenProfile(message.senderId) }
                    )
                } else {
                    Spacer(Modifier.width(AvatarSize))
                }
                Spacer(Modifier.width(8.dp))
            }
            if (own) SendStatus(message.sendState, Modifier.padding(end = 6.dp, bottom = 6.dp))
            Column(horizontalAlignment = if (own) Alignment.End else Alignment.Start) {
                if (showAvatar && startsRun) {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.labelLarge,
                        color = senderColor(message.senderId),
                        maxLines = 1,
                        modifier = Modifier
                            .clickable(role = Role.Button) { actions.onOpenProfile(message.senderId) }
                            .padding(start = 14.dp, bottom = 4.dp)
                    )
                }
                Bubble(message, startsRun, endsRun, actions)
                if (message.reactions.isNotEmpty()) {
                    ReactionRow(
                        reactions = message.reactions,
                        onToggle = { actions.onToggleReaction(message, it) },
                        onShowReactors = { actions.onShowReactors(message, it) },
                        // Tucked under the bubble's edge, so they read as part of it, not a row below.
                        modifier = Modifier
                            .overlapAbove(ReactionOverlap)
                            .padding(horizontal = 8.dp)
                            .padding(bottom = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * A message that arrives while the conversation is open rises from the bottom into place;
 * messages already there when it opened, and older pages, just appear. An own message animates
 * only as its local echo: once sent it can come back as a fresh row, and must not animate twice.
 */
@Composable
private fun Modifier.appear(isNew: Boolean, own: Boolean, sent: Boolean): Modifier {
    val reducedMotion = LocalReducedMotion.current
    val animate = isNew && !reducedMotion && !(own && sent)
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    val spring = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(Unit) { if (progress.value < 1f) progress.animateTo(1f, spring) }
    return graphicsLayer {
        val value = progress.value
        transformOrigin = TransformOrigin(if (own) 1f else 0f, 1f)
        translationY = (1f - value) * APPEAR_RISE.toPx()
        scaleX = APPEAR_SCALE + (1f - APPEAR_SCALE) * value
        scaleY = APPEAR_SCALE + (1f - APPEAR_SCALE) * value
        alpha = value.coerceIn(0f, 1f)
    }
}

/** Only shown while something is pending or wrong; a sent message needs no badge. */
@Composable
private fun SendStatus(state: SendState, modifier: Modifier = Modifier) {
    when (state) {
        SendState.Sent -> Unit
        SendState.Sending -> Icon(
            Symbols.Schedule,
            contentDescription = stringResource(R.string.timeline_sending),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.size(14.dp)
        )
        SendState.Failed -> Icon(
            Symbols.ErrorOutline,
            contentDescription = stringResource(R.string.timeline_failed),
            tint = MaterialTheme.colorScheme.error,
            modifier = modifier.size(16.dp)
        )
    }
}

private fun formatTime(millis: Long): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(message: TimelineEntry.Message, startsRun: Boolean, endsRun: Boolean, actions: MessageActions) {
    val own = message.isOwn
    val layout = bubbleLayout(message)
    // Pictures keep a gentler curve than text, so their corners aren't cut away.
    val outer = if (layout == BubbleLayout.Media) MediaRadius else LargeRadius
    val top = if (startsRun) outer else SeamRadius
    val bottom = if (endsRun) outer else SeamRadius
    val shape = RoundedCornerShape(
        topStart = if (own) outer else top,
        topEnd = if (own) top else outer,
        bottomEnd = if (own) bottom else outer,
        bottomStart = if (own) outer else bottom
    )
    val colors = MaterialTheme.colorScheme

    // Pressing sinks the bubble a little, the Expressive cue that a long press will act on it.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) PRESSED_SCALE else 1f, MaterialTheme.motionScheme.fastSpatialSpec())
    val haptics = LocalHapticFeedback.current
    val onLongPress = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        actions.onLongPress(message)
    }

    val container = when {
        layout == BubbleLayout.Bare || layout == BubbleLayout.Media -> Color.Transparent
        own -> colors.primary
        else -> colors.surfaceContainerHigh
    }
    Surface(
        shape = shape,
        color = container,
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
                onLongClick = onLongPress
            )
    ) {
        // A bare bubble shows the timeline through it.
        val ground = if (container == Color.Transparent) colors.surface else container
        CompositionLocalProvider(LocalBubbleColor provides ground) {
            BubbleContent(message, layout, actions, onLongPress)
        }
    }
}

/** A stable colour per sender from the theme's accent roles, so names stay legible in any palette. */
@Composable
private fun senderColor(senderId: String): Color {
    val colors = MaterialTheme.colorScheme
    val palette = listOf(colors.primary, colors.tertiary, colors.secondary)
    return palette[Math.floorMod(senderId.hashCode(), palette.size)]
}
