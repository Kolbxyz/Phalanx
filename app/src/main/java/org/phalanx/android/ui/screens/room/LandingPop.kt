package org.phalanx.android.ui.screens.room

import androidx.compose.animation.core.Animatable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import org.phalanx.android.data.model.TimelineEntry

private const val FRESH_MILLIS = 10_000L
private const val START_SCALE = 0.4f
private const val TILT_DEGREES = -12f

/**
 * A sticker or a few emoji that just arrived spring in from small, tilting upright; one older
 * than a few seconds, scrolled back to or loaded with the room, simply shows.
 */
@Composable
internal fun Modifier.landingPop(message: TimelineEntry.Message): Modifier {
    // Decided once per message: a pop is for arriving, not for every recomposition.
    val fresh = remember(message.id) { System.currentTimeMillis() - message.timestampMillis < FRESH_MILLIS }
    val scale = remember(message.id) { Animatable(if (fresh) START_SCALE else 1f) }
    val spring = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    LaunchedEffect(message.id) { if (fresh) scale.animateTo(1f, spring) }
    // Read while drawing, so the spring redraws the message without recomposing it.
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        rotationZ = (1f - scale.value) / (1f - START_SCALE) * TILT_DEGREES
    }
}
