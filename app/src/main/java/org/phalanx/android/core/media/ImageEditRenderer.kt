package org.phalanx.android.core.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.min

private const val BRUSH_FRACTION = 0.012f
private const val BLUR_BRUSH_FRACTION = 0.05f
private const val BLUR_DETAIL = 24

/** Decodes [file] with its longest side at most about [maxSide] px. */
internal fun decodeImageFile(file: File, maxSide: Int): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeFile(file.path, options) ?: throw IOException("Unreadable image")
}

/** The picture turned and mirrored, with every mark drawn on it; not yet cropped. */
fun Bitmap.withEdits(edits: ImageEdits): Bitmap {
    val marked = copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(marked)
    drawBlurs(canvas, this, edits.marks.filter { it.kind == Mark.Kind.Blur })
    edits.marks.filter { it.kind == Mark.Kind.Ink }.forEach { canvas.drawPath(it.path(width, height), it.paint(width, height)) }
    val matrix = Matrix().apply {
        postRotate(edits.turns * 90f)
        if (edits.mirrored) postScale(-1f, 1f)
    }
    return Bitmap.createBitmap(marked, 0, 0, width, height, matrix, true)
}

/** [withEdits] followed by the crop: what is sent. */
fun Bitmap.edited(edits: ImageEdits): Bitmap {
    val shown = withEdits(edits)
    val crop = edits.crop
    if (crop == UnitRect.Full) return shown
    val left = (crop.left * shown.width).toInt().coerceIn(0, shown.width - 1)
    val top = (crop.top * shown.height).toInt().coerceIn(0, shown.height - 1)
    val right = (crop.right * shown.width).toInt().coerceIn(left + 1, shown.width)
    val bottom = (crop.bottom * shown.height).toInt().coerceIn(top + 1, shown.height)
    return Bitmap.createBitmap(shown, left, top, right - left, bottom - top)
}

/** The picture, blurred, shows through only where a blur mark has been painted. */
private fun drawBlurs(canvas: Canvas, source: Bitmap, blurs: List<Mark>) {
    if (blurs.isEmpty()) return
    // Scaled far down and back up with filtering: a cheap blur that hides detail for good.
    val scale = BLUR_DETAIL.toFloat() / min(source.width, source.height)
    val small = source.scale(
        (source.width * scale).toInt().coerceAtLeast(1),
        (source.height * scale).toInt().coerceAtLeast(1)
    )
    val blurred = small.scale(source.width, source.height)
    val layer = createBitmap(source.width, source.height)
    val layerCanvas = Canvas(layer)
    blurs.forEach { layerCanvas.drawPath(it.path(source.width, source.height), it.paint(source.width, source.height)) }
    layerCanvas.drawBitmap(blurred, 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN) })
    canvas.drawBitmap(layer, 0f, 0f, null)
}

private fun Mark.path(width: Int, height: Int): Path = Path().apply {
    val first = points.first()
    moveTo(first.x * width, first.y * height)
    // A tap is a dot: a zero-length line with round caps.
    if (points.size == 1) lineTo(first.x * width + 0.1f, first.y * height)
    points.drop(1).forEach { lineTo(it.x * width, it.y * height) }
}

private fun Mark.paint(width: Int, height: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
    strokeWidth = min(width, height) * if (kind == Mark.Kind.Blur) BLUR_BRUSH_FRACTION else BRUSH_FRACTION
    color = if (kind == Mark.Kind.Blur) android.graphics.Color.BLACK else argb
}
