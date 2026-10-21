package org.phalanx.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.phalanx.android.R

/*
 * Static skeletons shown only before the very first data arrives. No shimmer: an endless animation
 * keeps the GPU awake for nothing, and the real content replaces these within moments.
 */

private const val ROOM_ROWS = 8

// Bubble widths as fractions of the row, alternating sides, so the skeleton reads as a chat.
private val bubbles = listOf(0.55f to false, 0.4f to true, 0.7f to false, 0.3f to false, 0.6f to true, 0.45f to false)

@Composable
internal fun RoomListPlaceholder(padding: PaddingValues) {
    val description = stringResource(R.string.loading)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 16.dp)
            .clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(SegmentGap)
    ) {
        repeat(ROOM_ROWS) { index -> RoomRowPlaceholder(index, ROOM_ROWS) }
    }
}

@Composable
private fun RoomRowPlaceholder(index: Int, count: Int) {
    Surface(shape = segmentedShape(index, count), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(MaterialShapes.Cookie4Sided.toShape())
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Bar(fraction = 0.45f, height = 14.dp)
                Bar(fraction = 0.8f, height = 12.dp)
            }
        }
    }
}

/** Laid out bottom-up, like the timeline it stands in for. */
@Composable
internal fun TimelinePlaceholder(padding: PaddingValues) {
    val description = stringResource(R.string.loading)
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom)
    ) {
        bubbles.forEach { (fraction, own) ->
            Box(Modifier.fillMaxWidth(), contentAlignment = if (own) Alignment.CenterEnd else Alignment.CenterStart) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (own) colors.primaryContainer else colors.surfaceContainerHigh)
                )
            }
        }
    }
}

@Composable
private fun Bar(fraction: Float, height: Dp) {
    Box(
        Modifier
            .fillMaxWidth(fraction)
            .height(height)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    )
}
