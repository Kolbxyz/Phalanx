package org.phalanx.android.ui.screens.room

import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.phalanx.android.ui.theme.LocalReducedMotion
import org.phalanx.android.ui.theme.Symbols
import kotlin.math.roundToInt

private val ReplyThreshold = 64.dp
private val RevealWidth = 72.dp
private const val RESISTANCE = 0.6f
private const val MIN_ICON_SCALE = 0.6f

/** How far the whole conversation is dragged left to show message times; shared by every row. */
@Stable
internal class TimestampReveal {
    var offset by mutableFloatStateOf(0f)
}

internal val LocalTimestampReveal = staticCompositionLocalOf { TimestampReveal() }

/**
 * A message row's horizontal gestures, in one place so they never fight: dragging right replies
 * (past [ReplyThreshold], with a haptic tick) and springs back; dragging left shows every row's
 * [time], as in Google Messages, and stays open once dragged past half way, until dragged back.
 * Every row slides over, so a wide bubble can never sit under its time.
 */
@Composable
internal fun MessageSwipe(
    time: String,
    canReply: Boolean,
    onReply: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val reveal = LocalTimestampReveal.current
    // Plain state written synchronously on every delta; a coroutine per delta could land after the
    // release animation started and cancel it, leaving the row stranded.
    var reply by remember { mutableFloatStateOf(0f) }
    val settle = remember { arrayOfNulls<Job>(1) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val threshold = with(density) { ReplyThreshold.toPx() }
    val revealWidth = with(density) { RevealWidth.toPx() }
    val spring = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val reducedMotion = LocalReducedMotion.current

    fun settle() {
        val revealTarget = if (reveal.offset < -revealWidth / 2) -revealWidth else 0f
        settle[0] = scope.launch {
            if (reducedMotion) {
                reply = 0f
                reveal.offset = revealTarget
                return@launch
            }
            launch { animate(reply, 0f, animationSpec = spring) { value, _ -> reply = value } }
            animate(reveal.offset, revealTarget, animationSpec = spring) { value, _ -> reveal.offset = value }
        }
    }

    Box(
        modifier = modifier.fillMaxWidth().draggable(
            orientation = Orientation.Horizontal,
            state = rememberDraggableState { delta ->
                val replying = reply > 0f || (canReply && delta > 0f && reveal.offset == 0f)
                if (replying) {
                    val after = (reply + delta * RESISTANCE).coerceIn(0f, threshold * 1.4f)
                    if (reply < threshold && after >= threshold) {
                        haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                    }
                    reply = after
                } else {
                    reveal.offset = (reveal.offset + delta).coerceIn(-revealWidth, 0f)
                }
            },
            onDragStarted = { settle[0]?.cancel() },
            onDragStopped = {
                if (reply >= threshold) onReply()
                settle()
            }
        )
    ) {
        val replyProgress = (reply / threshold).coerceIn(0f, 1f)
        val revealProgress = (-reveal.offset / revealWidth).coerceIn(0f, 1f)
        Icon(
            Symbols.Reply,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 8.dp)
                .alpha(replyProgress)
                .scale(MIN_ICON_SCALE + (1f - MIN_ICON_SCALE) * replyProgress)
        )
        Box(Modifier.offset { IntOffset((reply + reveal.offset).roundToInt(), 0) }) { content() }
        Text(
            text = time,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.End,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(RevealWidth)
                .offset { IntOffset((revealWidth + reveal.offset).roundToInt(), 0) }
                .alpha(revealProgress)
        )
    }
}
