package org.phalanx.android.core.media

import androidx.compose.runtime.Immutable

/** A point as a fraction of the picture's width and height, so it means the same at any resolution. */
@Immutable
data class UnitPoint(val x: Float, val y: Float)

/** A freehand line over the picture. [argb] is ignored for [Mark.Kind.Blur]. */
@Immutable
data class Mark(val kind: Kind, val argb: Int, val points: List<UnitPoint>) {
    enum class Kind { Ink, Blur }
}

/** The part of the displayed picture that is kept, as fractions of it. */
@Immutable
data class UnitRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    /** This rect after the picture it is on is turned a quarter clockwise. */
    fun turned() = UnitRect(1 - bottom, left, 1 - top, right)

    fun mirrored() = UnitRect(1 - right, top, 1 - left, bottom)

    companion object {
        val Full = UnitRect(0f, 0f, 1f, 1f)
    }
}

/**
 * What the user did to a picture. [marks] are in the source picture's space; [turns] and [mirrored]
 * make the displayed picture (turned first, then mirrored), and [crop] is in that displayed space.
 */
@Immutable
data class ImageEdits(
    val marks: List<Mark> = emptyList(),
    val turns: Int = 0,
    val mirrored: Boolean = false,
    val crop: UnitRect = UnitRect.Full
) {
    val isUnchanged: Boolean
        get() = this == ImageEdits()

    fun rotated() = copy(
        // Turning what is seen clockwise turns a mirrored source the other way.
        turns = Math.floorMod(turns + if (mirrored) -1 else 1, QUARTERS),
        crop = crop.turned()
    )

    fun mirroredAcross() = copy(mirrored = !mirrored, crop = crop.mirrored())

    /** A point of the source picture, where it lands in the displayed one. */
    fun toDisplay(p: UnitPoint): UnitPoint {
        var x = p.x
        var y = p.y
        repeat(turns) {
            val turnedX = 1 - y
            y = x
            x = turnedX
        }
        return UnitPoint(if (mirrored) 1 - x else x, y)
    }

    /** A point of the displayed picture, where it lies on the source one. */
    fun toSource(p: UnitPoint): UnitPoint {
        var x = if (mirrored) 1 - p.x else p.x
        var y = p.y
        repeat(turns) {
            val unturnedY = 1 - x
            x = y
            y = unturnedY
        }
        return UnitPoint(x, y)
    }

    private companion object {
        const val QUARTERS = 4
    }
}
