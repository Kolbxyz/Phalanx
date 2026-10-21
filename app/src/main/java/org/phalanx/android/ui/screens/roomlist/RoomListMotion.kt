package org.phalanx.android.ui.screens.roomlist

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.phalanx.android.ui.theme.LocalReducedMotion

private const val STAGGER_MILLIS = 35L
private const val MAX_STAGGERED = 12
private const val PRESSED_SCALE = 0.97f
private val EntranceRise = 28.dp

/**
 * Rows on screen when the list first shows rise into place one after another; rows scrolled to
 * later, or added afterwards, are left to the list's own item animations.
 */
@Composable
internal fun Modifier.listEntrance(index: Int, intro: Boolean): Modifier {
    val reducedMotion = LocalReducedMotion.current
    val progress = remember { Animatable(if (intro && !reducedMotion && index < MAX_STAGGERED) 0f else 1f) }
    val spring = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(Unit) {
        if (progress.value < 1f) {
            delay(index * STAGGER_MILLIS)
            progress.animateTo(1f, spring)
        }
    }
    val rise = with(LocalDensity.current) { EntranceRise.toPx() }
    return graphicsLayer {
        val value = progress.value
        alpha = value.coerceIn(0f, 1f)
        translationY = (1f - value) * rise
    }
}

/** Pressing sinks the row a little, the Expressive cue that a long press will act on it. */
@Composable
internal fun Modifier.pressSink(interaction: InteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) PRESSED_SCALE else 1f, MaterialTheme.motionScheme.fastSpatialSpec())
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
