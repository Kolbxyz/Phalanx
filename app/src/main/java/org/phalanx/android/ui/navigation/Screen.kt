package org.phalanx.android.ui.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlin.jvm.JvmSuppressWildcards

private typealias Enter = AnimatedContentTransitionScope<NavBackStackEntry>.() -> @JvmSuppressWildcards EnterTransition?
private typealias Exit = AnimatedContentTransitionScope<NavBackStackEntry>.() -> @JvmSuppressWildcards ExitTransition?

/** The entry on top of the back stack, provided around the nav host. */
internal val LocalTopEntry = staticCompositionLocalOf<NavBackStackEntry?> { null }

/**
 * A destination whose screen lets touches through once it is on its way out. The nav host keeps a
 * closing screen above the one coming back until its exit settles, and Compose hands a touch to the
 * topmost screen it hits, so taps would otherwise press the screen just left or be lost to it.
 */
internal inline fun <reified T : Any> NavGraphBuilder.screen(
    noinline enterTransition: Enter? = null,
    noinline exitTransition: Exit? = null,
    noinline popEnterTransition: Enter? = enterTransition,
    noinline popExitTransition: Exit? = exitTransition,
    noinline content: @Composable (NavBackStackEntry) -> Unit
) = composable<T>(
    enterTransition = enterTransition,
    exitTransition = exitTransition,
    popEnterTransition = popEnterTransition,
    popExitTransition = popExitTransition
) { entry ->
    CompositionLocalProvider(LocalNavAnimatedScope provides this) {
        TouchesWhileShown(this, entry) { content(entry) }
    }
}

/**
 * While [entry] leaves, its touches are swallowed; once another entry is on top, the screen is
 * frozen into a picture of its last frame and its content unplaced, so it holds no hit target at
 * all and taps reach the screen underneath. Screens sharing an element with the media viewer stay
 * live instead, as the element morphs out of their content.
 */
@Composable
internal fun TouchesWhileShown(scope: AnimatedContentScope, entry: NavBackStackEntry, content: @Composable () -> Unit) {
    val leaving = scope.transition.targetState != EnterExitState.Visible
    val handedOver = leaving && handsOver(entry, LocalTopEntry.current)
    val layer = rememberGraphicsLayer()
    var frozen by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(handedOver) { frozen = if (handedOver) layer.toImageBitmap() else null }
    val still = frozen.takeIf { handedOver }

    val drawing = Modifier.drawWithContent {
        if (still != null) {
            drawImage(still)
        } else {
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
        }
    }
    val touches = if (leaving && still == null) {
        // Consumed on the way down, before any child sees it, so nothing under it reacts.
        Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        }
    } else {
        Modifier
    }
    Layout(content, drawing.then(touches)) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        val width = placeables.maxOfOrNull { it.width } ?: constraints.minWidth
        val height = placeables.maxOfOrNull { it.height } ?: constraints.minHeight
        layout(width, height) {
            // Unplaced content keeps its state but is skipped by hit testing.
            if (still == null) placeables.forEach { it.place(0, 0) }
        }
    }
}

/** Another entry is on top, and neither shares an element with the other through the media viewer. */
private fun handsOver(entry: NavBackStackEntry, top: NavBackStackEntry?) =
    top != null && top !== entry && !top.isMedia() && !entry.isMedia()

private fun NavBackStackEntry.isMedia() = destination.hasRoute<MediaViewerRoute>()
