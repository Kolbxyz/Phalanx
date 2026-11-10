package org.phalanx.android.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.launch
import org.phalanx.android.ui.theme.LocalReducedMotion

/**
 * A profile's avatar on a halo of the same wavy shape, one size up: the screen's one expressive
 * flourish. Both grow and turn into place once when it opens and then rest; a halo that kept
 * turning would redraw the screen for as long as it stays open, for no information.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HaloAvatar(
    haloSize: Dp,
    modifier: Modifier = Modifier,
    halo: Color = MaterialTheme.colorScheme.tertiaryContainer,
    avatar: @Composable () -> Unit
) {
    val reduced = LocalReducedMotion.current
    val scale = remember { Animatable(if (reduced) 1f else ENTRY_SCALE) }
    val turn = remember { Animatable(if (reduced) 0f else ENTRY_TURN) }
    val spatial = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(Unit) {
        launch { scale.animateTo(1f, spatial) }
        launch { turn.animateTo(0f, spatial) }
    }
    val shape = MaterialShapes.Cookie9Sided.toShape()
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(haloSize).graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
    ) {
        Box(
            Modifier
                .size(haloSize)
                .graphicsLayer { rotationZ = turn.value }
                .clip(shape)
                .background(halo)
        )
        avatar()
    }
}

private const val ENTRY_SCALE = 0.4f
private const val ENTRY_TURN = -40f
