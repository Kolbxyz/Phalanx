package org.phalanx.android.core.media

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.IOException
import kotlin.math.max

/**
 * Decodes [uri] with its longest side at most about [maxSide] px, in software so it can be cropped.
 * Only pixels come out: whatever EXIF the file carried is gone once they are encoded again.
 */
internal fun ContentResolver.decodeScaled(uri: Uri, maxSide: Int): Bitmap {
    // ImageDecoder applies EXIF rotation, so a portrait photo is not uploaded sideways.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(this, uri)) { decoder, info, _ ->
            val longest = max(info.size.width, info.size.height)
            if (longest > maxSide) {
                val ratio = maxSide.toFloat() / longest
                decoder.setTargetSize((info.size.width * ratio).toInt(), (info.size.height * ratio).toInt())
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        ?: throw IOException("Unreadable image")
}
