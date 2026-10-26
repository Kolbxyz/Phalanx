package org.phalanx.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** A button shrinks a little while held, the Expressive cue that the press landed. */
const val BUTTON_PRESSED_SCALE = 0.88f

/** Scales to [pressed] while [interaction] is pressed, on the theme's fast spatial spring. */
@Composable
fun Modifier.pressShrink(interaction: InteractionSource, pressed: Float = BUTTON_PRESSED_SCALE): Modifier {
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) pressed else 1f, MaterialTheme.motionScheme.fastSpatialSpec())
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
