package org.phalanx.android.ui.screens.room

import androidx.compose.animation.core.animate
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.phalanx.android.ui.theme.LocalReducedMotion
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
    // Plain state written synchronously on every delta: queuing a coroutine per delta let the last
    // ones land after the release animation had started, cancel it, and leave the bubble stranded.
    var offset by remember { mutableFloatStateOf(0f) }
    val settle = remember { arrayOfNulls<Job>(1) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val threshold = with(LocalDensity.current) { Threshold.toPx() }
    val spring = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val reducedMotion = LocalReducedMotion.current
    val progress = (offset / threshold).coerceIn(0f, 1f)

    Box(
        modifier = Modifier.draggable(
            orientation = Orientation.Horizontal,
            state = rememberDraggableState { delta ->
                val after = (offset + delta * RESISTANCE).coerceIn(0f, threshold * 1.4f)
                if (offset < threshold && after >= threshold) {
                    haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                }
                offset = after
            },
            onDragStarted = { settle[0]?.cancel() },
            onDragStopped = {
                if (offset >= threshold) onReply()
                // The composition's scope, not the gesture's, so nothing but a new drag stops the return.
                settle[0] = scope.launch {
                    if (reducedMotion) {
                        offset = 0f
                    } else {
                        animate(offset, 0f, animationSpec = spring) { value, _ -> offset = value }
                    }
                }
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
        Box(Modifier.offset { IntOffset(offset.roundToInt(), 0) }) { content() }
    }
}
