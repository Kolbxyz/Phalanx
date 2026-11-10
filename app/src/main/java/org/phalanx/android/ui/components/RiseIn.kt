package org.phalanx.android.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.phalanx.android.ui.theme.LocalReducedMotion

/**
 * Rises into place once, [delayMillis] after it first shows: give a run of items growing delays and
 * they arrive one after another, the order a reader takes them in. [from] is the starting scale.
 */
@Composable
internal fun Modifier.riseIn(delayMillis: Long, rise: Dp = 14.dp, from: Float = 0.96f): Modifier {
    val reduced = LocalReducedMotion.current
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    val spring = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(Unit) {
        if (progress.value < 1f) {
            delay(delayMillis)
            progress.animateTo(1f, spring)
        }
    }
    val risePx = with(LocalDensity.current) { rise.toPx() }
    return graphicsLayer {
        val value = progress.value
        alpha = value.coerceIn(0f, 1f)
        translationY = (1f - value) * risePx
        val scale = from + (1f - from) * value
        scaleX = scale
        scaleY = scale
    }
}

/** The delay of the [index]th item of a run: [first] before the first, then [step] between each. */
internal fun stagger(index: Int, first: Long = 0L, step: Long = 50L): Long = first + index * step
