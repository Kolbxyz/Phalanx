package org.phalanx.android.ui.screens.room

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** The row ID of the message lifted into its menu, if any. */
internal val LocalLiftedMessage = compositionLocalOf<String?> { null }

/** Leaves a gap where the message was while it is lifted into its menu, so it reads as picked up. */
@Composable
internal fun Modifier.liftedAway(id: String): Modifier {
    val presence by animateFloatAsState(
        if (LocalLiftedMessage.current == id) 0f else 1f,
        MaterialTheme.motionScheme.fastEffectsSpec()
    )
    return graphicsLayer { alpha = presence }
}
