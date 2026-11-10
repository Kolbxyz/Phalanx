package org.phalanx.android.ui.screens.roomlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import kotlinx.coroutines.delay
import org.phalanx.android.ui.components.pressShrink
import org.phalanx.android.ui.theme.LocalReducedMotion

private const val STAGGER_MILLIS = 35L
private const val MAX_STAGGERED = 12
private const val PRESSED_SCALE = 0.97f
private val EntranceRise = 28.dp
private val SearchFromEnd = 76.dp
private val SearchFromTop = 36.dp

/**
 * Opens the search as a circle growing out of the search button until it covers the screen, and
 * closes it back into the button, so it reads as the button unfolding rather than a new page.
 */
@Composable
internal fun SearchReveal(visible: Boolean, content: @Composable () -> Unit) {
    val motion = MaterialTheme.motionScheme
    val density = LocalDensity.current
    val top = WindowInsets.statusBars.getTop(density)
    AnimatedVisibility(visible = visible, enter = EnterTransition.None, exit = ExitTransition.None) {
        val open by transition.animateFloat(
            transitionSpec = {
                if (targetState == EnterExitState.Visible) motion.defaultSpatialSpec() else motion.fastEffectsSpec()
            }
        ) { if (it == EnterExitState.Visible) 1f else 0f }
        Box(
            Modifier.drawWithContent {
                val center = Offset(size.width - SearchFromEnd.toPx(), top + SearchFromTop.toPx())
                val reach = hypot(size.width, size.height)
                val circle = Path().apply { addOval(Rect(center, reach * open.coerceIn(0f, 1f))) }
                clipPath(circle) { this@drawWithContent.drawContent() }
            }
        ) { content() }
    }
}

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
internal fun Modifier.pressSink(interaction: InteractionSource): Modifier = pressShrink(interaction, PRESSED_SCALE)
