package org.phalanx.android.ui.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect

/** Provided around the nav host, so a screen can share elements with the next one. */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/** The enter/exit scope of the destination currently being composed. */
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Marks the media identified by [key] so it grows from its bubble into the viewer and back. Uses
 * the theme's spatial spec, so reduced motion turns it into a cut.
 */
@Composable
internal fun Modifier.sharedMedia(key: String): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val animated = LocalNavAnimatedScope.current ?: return this
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Rect>()
    return with(shared) {
        this@sharedMedia.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animated,
            boundsTransform = BoundsTransform { _, _ -> spec }
        )
    }
}
