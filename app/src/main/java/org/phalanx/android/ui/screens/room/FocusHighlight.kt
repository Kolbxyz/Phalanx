package org.phalanx.android.ui.screens.room

import androidx.compose.animation.core.Animatable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val HIGHLIGHT_ALPHA = 0.24f
private const val HIGHLIGHT_HOLD_MILLIS = 900L

/** A tint behind the row while [active], held a moment then faded out, after which [onDone] runs. */
@Composable
internal fun Modifier.focusHighlight(active: Boolean, onDone: () -> Unit): Modifier {
    val alpha = remember { Animatable(0f) }
    val fade = MaterialTheme.motionScheme.slowEffectsSpec<Float>()
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        alpha.snapTo(HIGHLIGHT_ALPHA)
        delay(HIGHLIGHT_HOLD_MILLIS)
        alpha.animateTo(0f, fade)
        onDone()
    }
    val color = MaterialTheme.colorScheme.primary
    // Read while drawing, so the fade redraws the row without recomposing it.
    return drawBehind {
        if (alpha.value > 0f) {
            drawRoundRect(color.copy(alpha = alpha.value), cornerRadius = CornerRadius(16.dp.toPx()))
        }
    }
}
