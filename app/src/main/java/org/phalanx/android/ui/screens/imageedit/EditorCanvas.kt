package org.phalanx.android.ui.screens.imageedit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.min
import org.phalanx.android.core.media.ImageEdits
import org.phalanx.android.core.media.Mark
import org.phalanx.android.core.media.UnitPoint
import org.phalanx.android.core.media.UnitRect

private val HandleRadius = 9.dp
private val GrabSlop = 28.dp
private const val DIM_ALPHA = 0.55f
private const val INK_FRACTION = 0.012f
private const val BLUR_FRACTION = 0.05f
private const val BLUR_PREVIEW_ALPHA = 0.5f

/** The picture, fitted to the space, with the crop frame over it and a finger drawing on it. */
@Composable
internal fun EditorCanvas(
    image: ImageBitmap,
    edits: ImageEdits,
    tool: EditorTool,
    inkColor: Color,
    onMark: (Mark) -> Unit,
    onCrop: (UnitRect) -> Unit,
    modifier: Modifier = Modifier
) {
    var space by remember { mutableStateOf(IntSize.Zero) }
    // The stroke being drawn, in the displayed picture's own fractions.
    var live by remember { mutableStateOf<List<UnitPoint>>(emptyList()) }
    var grab by remember { mutableStateOf<CropGrab?>(null) }
    val currentEdits by rememberUpdatedState(edits)
    val currentColor by rememberUpdatedState(inkColor)
    val fit = remember(space, image) { Fit.of(space, image.width, image.height) }

    Canvas(
        modifier
            .fillMaxSize()
            .onSizeChanged { space = it }
            .pointerInput(fit, tool) {
                val slop = GrabSlop.toPx()
                detectDragGestures(
                    onDragStart = { at ->
                        val start = fit.toUnit(at)
                        if (tool == EditorTool.Crop) {
                            grab = currentEdits.crop.grabAt(start, slop / fit.width, slop / fit.height)
                        } else {
                            live = listOf(start)
                        }
                    },
                    onDrag = { change, delta ->
                        change.consume()
                        if (tool == EditorTool.Crop) {
                            grab?.let { current ->
                                onCrop(currentEdits.crop.dragged(current, delta.x / fit.width, delta.y / fit.height))
                            }
                        } else {
                            live = live + fit.toUnit(change.position)
                        }
                    },
                    onDragEnd = {
                        if (tool != EditorTool.Crop && live.isNotEmpty()) {
                            val kind = if (tool == EditorTool.Blur) Mark.Kind.Blur else Mark.Kind.Ink
                            onMark(Mark(kind, currentColor.toArgb(), live.map(currentEdits::toSource)))
                        }
                        live = emptyList()
                        grab = null
                    },
                    onDragCancel = {
                        live = emptyList()
                        grab = null
                    }
                )
            }
    ) {
        drawImage(
            image,
            dstOffset = IntOffset(fit.left.toInt(), fit.top.toInt()),
            dstSize = IntSize(fit.width.toInt(), fit.height.toInt())
        )
        drawLive(live, fit, tool, inkColor)
        drawCrop(edits.crop, fit, showHandles = tool == EditorTool.Crop)
    }
}

private fun DrawScope.drawLive(points: List<UnitPoint>, fit: Fit, tool: EditorTool, color: Color) {
    if (points.isEmpty()) return
    val path = Path().apply {
        val startX = fit.left + points.first().x * fit.width
        val startY = fit.top + points.first().y * fit.height
        moveTo(startX, startY)
        if (points.size == 1) lineTo(startX + 0.1f, startY)
        points.drop(1).forEach { lineTo(fit.left + it.x * fit.width, fit.top + it.y * fit.height) }
    }
    val blur = tool == EditorTool.Blur
    drawPath(
        path = path,
        color = if (blur) Color.White.copy(alpha = BLUR_PREVIEW_ALPHA) else color,
        style = Stroke(
            width = min(fit.width, fit.height) * if (blur) BLUR_FRACTION else INK_FRACTION,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

/** Dims what the crop will remove and outlines what it keeps. */
private fun DrawScope.drawCrop(crop: UnitRect, fit: Fit, showHandles: Boolean) {
    val left = fit.left + crop.left * fit.width
    val top = fit.top + crop.top * fit.height
    val right = fit.left + crop.right * fit.width
    val bottom = fit.top + crop.bottom * fit.height
    val dim = Color.Black.copy(alpha = DIM_ALPHA)
    drawRect(dim, Offset(fit.left, fit.top), Size(fit.width, top - fit.top))
    drawRect(dim, Offset(fit.left, bottom), Size(fit.width, fit.top + fit.height - bottom))
    drawRect(dim, Offset(fit.left, top), Size(left - fit.left, bottom - top))
    drawRect(dim, Offset(right, top), Size(fit.left + fit.width - right, bottom - top))
    if (!showHandles) return
    drawRect(Color.White, Offset(left, top), Size(right - left, bottom - top), style = Stroke(2.dp.toPx()))
    listOf(left, right).forEach { x ->
        listOf(top, bottom).forEach { y -> drawCircle(Color.White, HandleRadius.toPx(), Offset(x, y)) }
    }
}

/** Where the picture sits in the space: as large as fits, centred. */
internal data class Fit(val left: Float, val top: Float, val width: Float, val height: Float) {
    fun toUnit(at: Offset) = UnitPoint(
        ((at.x - left) / width).coerceIn(0f, 1f),
        ((at.y - top) / height).coerceIn(0f, 1f)
    )

    companion object {
        fun of(space: IntSize, imageWidth: Int, imageHeight: Int): Fit {
            if (space.width == 0 || space.height == 0) return Fit(0f, 0f, 1f, 1f)
            val scale = min(space.width.toFloat() / imageWidth, space.height.toFloat() / imageHeight)
            val width = imageWidth * scale
            val height = imageHeight * scale
            return Fit((space.width - width) / 2, (space.height - height) / 2, width, height)
        }
    }
}
