package org.phalanx.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * A one-pixel line under a top bar that shows only once the content has scrolled beneath it, so the
 * bar keeps one colour and the edge is drawn just when there is something to separate.
 */
@Composable
internal fun ScrollHairline(visible: Boolean, modifier: Modifier = Modifier) {
    val alpha by animateFloatAsState(if (visible) 1f else 0f, MaterialTheme.motionScheme.fastEffectsSpec())
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .graphicsLayer { this.alpha = alpha }
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
internal fun LazyListState.scrolledPastTop(): Boolean {
    val scrolled by remember(this) {
        derivedStateOf { firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0 }
    }
    return scrolled
}

@Composable
internal fun ScrollState.scrolledPastTop(): Boolean {
    val scrolled by remember(this) { derivedStateOf { value > 0 } }
    return scrolled
}
