package org.phalanx.android.ui.screens.room

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val Threshold = 64.dp
private const val RESISTANCE = 0.6f
private const val MIN_ICON_SCALE = 0.6f

/**
 * Drag a message towards the end to reply, like most messengers. It springs back on release with
 * the theme's spatial motion; past [Threshold] it triggers [onReply] once, with a haptic tick.
 */
@Composable
internal fun SwipeToReply(enabled: Boolean, onReply: () -> Unit, content: @Composable () -> Unit) {
    if (!enabled) {
        content()
        return
    }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val threshold = with(LocalDensity.current) { Threshold.toPx() }
    val spring = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val progress = (offset.value / threshold).coerceIn(0f, 1f)

    Box(
        modifier = Modifier.draggable(
            orientation = Orientation.Horizontal,
            state = rememberDraggableState { delta ->
                val before = offset.value
                val after = (before + delta * RESISTANCE).coerceIn(0f, threshold * 1.4f)
                if (before < threshold && after >= threshold) {
                    haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                }
                scope.launch { offset.snapTo(after) }
            },
            onDragStopped = {
                if (offset.value >= threshold) onReply()
                offset.animateTo(0f, spring)
            }
        )
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.Reply,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 8.dp)
                .alpha(progress)
                .scale(MIN_ICON_SCALE + (1f - MIN_ICON_SCALE) * progress)
        )
        Box(Modifier.offset { IntOffset(offset.value.roundToInt(), 0) }) { content() }
    }
}
