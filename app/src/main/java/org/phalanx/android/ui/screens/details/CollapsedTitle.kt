package org.phalanx.android.ui.screens.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.phalanx.android.data.model.RoomSummary
import org.phalanx.android.ui.components.MatrixAvatar

/** Once the header has scrolled under the bar, its avatar and name rise into the bar instead. */
@Composable
internal fun CollapsedTitle(room: RoomSummary, shown: Boolean) {
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(motion.fastEffectsSpec()) + slideInVertically(motion.defaultSpatialSpec()) { it / 2 },
        exit = fadeOut(motion.fastEffectsSpec()) + slideOutVertically(motion.fastSpatialSpec()) { it / 2 }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val colors = MaterialTheme.colorScheme
            MatrixAvatar(
                name = room.name,
                media = room.avatar,
                size = 32.dp,
                shape = (if (room.isDirect) MaterialShapes.Cookie9Sided else MaterialShapes.Cookie4Sided).toShape(),
                container = if (room.isDirect) colors.tertiaryContainer else colors.primaryContainer,
                content = if (room.isDirect) colors.onTertiaryContainer else colors.onPrimaryContainer
            )
            Text(room.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** True once the header, the list's first item, has scrolled past where its name sits. */
@Composable
internal fun LazyListState.headerScrolledAway(): Boolean {
    val threshold = with(LocalDensity.current) { NAME_DEPTH.roundToPx() }
    val away by remember(this, threshold) {
        derivedStateOf { firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > threshold }
    }
    return away
}

// How far down the header its name ends: past this, the name is under the bar.
private val NAME_DEPTH = 190.dp
