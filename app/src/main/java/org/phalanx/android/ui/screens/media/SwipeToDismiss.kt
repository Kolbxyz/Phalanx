package org.phalanx.android.ui.screens.media

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.launch

private const val DISMISS_FRACTION = 0.2f
private val FlingVelocity = 1200.dp

/**
 * Dragging [content] up or down lets it go: far or fast enough and [onDismiss] is called, short of
 * that it springs back. [background] fades as it moves, showing the conversation underneath.
 */
@Composable
internal fun SwipeToDismiss(
    enabled: Boolean,
    background: Color,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val spring = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val flingPx = with(LocalDensity.current) { FlingVelocity.toPx() }
    var height by remember { mutableFloatStateOf(1f) }
    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { height = it.height.toFloat() }
            // Read while drawing only, so following the finger never recomposes the pages.
            .drawBehind {
                val progress = (abs(offset.value) / (size.height * DISMISS_FRACTION * 2)).coerceIn(0f, 1f)
                drawRect(background.copy(alpha = background.alpha * (1f - progress)))
            }
            .draggable(
                orientation = Orientation.Vertical,
                enabled = enabled,
                state = rememberDraggableState { delta -> scope.launch { offset.snapTo(offset.value + delta) } },
                onDragStopped = { velocity ->
                    if (abs(offset.value) > height * DISMISS_FRACTION || abs(velocity) > flingPx) {
                        onDismiss()
                    } else {
                        offset.animateTo(0f, spring)
                    }
                }
            )
    ) {
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = offset.value }) { content() }
    }
}
