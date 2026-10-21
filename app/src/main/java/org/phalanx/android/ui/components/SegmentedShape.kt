package org.phalanx.android.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The corners of one item in a segmented list: the group's outer corners are round and the seams
 * between items are tight, so a run of rows reads as one card cut into strips rather than as a
 * stack of separate cards.
 */
internal fun segmentedShape(index: Int, count: Int, outer: Dp = 24.dp, inner: Dp = 6.dp): Shape {
    val top = if (index == 0) outer else inner
    val bottom = if (index == count - 1) outer else inner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/** The gap between the strips of a segmented list. */
internal val SegmentGap = 2.dp
