package org.phalanx.android.ui.screens.imageedit

import org.phalanx.android.core.media.UnitPoint
import org.phalanx.android.core.media.UnitRect
import kotlin.math.abs

private const val MIN_SIDE = 0.1f

/** Which part of the crop frame a drag holds: edges and corners resize it, the inside moves it. */
internal data class CropGrab(
    val left: Boolean = false,
    val top: Boolean = false,
    val right: Boolean = false,
    val bottom: Boolean = false,
    val move: Boolean = false
)

/** What is under [at], given how far from an edge still counts as on it; null for outside the frame. */
internal fun UnitRect.grabAt(at: UnitPoint, slopX: Float, slopY: Float): CropGrab? {
    if (at.x !in left - slopX..right + slopX || at.y !in top - slopY..bottom + slopY) return null
    val grab = CropGrab(
        left = abs(at.x - left) < slopX,
        top = abs(at.y - top) < slopY,
        right = abs(at.x - right) < slopX,
        bottom = abs(at.y - bottom) < slopY
    )
    return if (grab.left || grab.top || grab.right || grab.bottom) grab else grab.copy(move = true)
}

/** The frame after [grab] was dragged by a fraction [dx], [dy] of the picture, kept inside it. */
internal fun UnitRect.dragged(grab: CropGrab, dx: Float, dy: Float): UnitRect {
    if (grab.move) {
        val shiftX = dx.coerceIn(-left, 1 - right)
        val shiftY = dy.coerceIn(-top, 1 - bottom)
        return UnitRect(left + shiftX, top + shiftY, right + shiftX, bottom + shiftY)
    }
    return UnitRect(
        left = if (grab.left) (left + dx).coerceIn(0f, right - MIN_SIDE) else left,
        top = if (grab.top) (top + dy).coerceIn(0f, bottom - MIN_SIDE) else top,
        right = if (grab.right) (right + dx).coerceIn(left + MIN_SIDE, 1f) else right,
        bottom = if (grab.bottom) (bottom + dy).coerceIn(top + MIN_SIDE, 1f) else bottom
    )
}
