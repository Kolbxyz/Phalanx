package org.phalanx.android.core.ai.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

/** The image could not be read as a picture. */
class UndecodableImageException : IOException("Undecodable image")

/** An image as the vision model takes it: [rgb] holds three bytes a pixel, row by row. */
internal class ImagePixels(val rgb: ByteArray, val width: Int, val height: Int)

/**
 * Reads the image at [uri], scaled down so its longer side is at most [MAX_SIDE]: past that the
 * model reads no more detail, only more tokens and time.
 */
internal fun pixelsOf(context: Context, uri: Uri): ImagePixels {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    val longer = max(bounds.outWidth, bounds.outHeight)
    if (longer <= 0) throw UndecodableImageException()
    var sample = 1
    while (longer / (sample * 2) >= MAX_SIDE) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        ?: throw UndecodableImageException()
    val scale = MAX_SIDE.toFloat() / max(decoded.width, decoded.height)
    val bitmap = if (scale < 1f) {
        val width = (decoded.width * scale).roundToInt()
        Bitmap.createScaledBitmap(decoded, width, (decoded.height * scale).roundToInt(), true)
    } else {
        decoded
    }
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    val rgb = ByteArray(pixels.size * CHANNELS)
    pixels.forEachIndexed { i, color ->
        rgb[i * CHANNELS] = (color shr RED_SHIFT).toByte()
        rgb[i * CHANNELS + 1] = (color shr GREEN_SHIFT).toByte()
        rgb[i * CHANNELS + 2] = color.toByte()
    }
    val result = ImagePixels(rgb, bitmap.width, bitmap.height)
    if (bitmap !== decoded) bitmap.recycle()
    decoded.recycle()
    return result
}

private const val MAX_SIDE = 768
private const val CHANNELS = 3
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
