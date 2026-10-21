package org.phalanx.android.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * Turns a picked image into an avatar upload: centre-cropped square, at most [MAX_SIDE] px, JPEG.
 * Re-encoding is the point as much as the size: the original file's EXIF (GPS position, camera,
 * time taken) never leaves the device, because only decoded pixels are written back out.
 */
@Singleton
class AvatarEncoder @Inject constructor(@ApplicationContext private val context: Context) {
    suspend fun encode(uri: Uri): ByteArray = withContext(Dispatchers.Default) {
        val decoded = decode(uri)
        val side = min(decoded.width, decoded.height)
        val square = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side)
        val target = min(side, MAX_SIDE)
        val scaled = if (target == side) square else Bitmap.createScaledBitmap(square, target, target, true)
        ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
    }

    private fun decode(uri: Uri): Bitmap {
        val resolver = context.contentResolver
        // ImageDecoder applies EXIF rotation, so a portrait photo is not uploaded sideways.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                val longest = max(info.size.width, info.size.height)
                if (longest > DECODE_SIDE) {
                    val ratio = DECODE_SIDE.toFloat() / longest
                    decoder.setTargetSize((info.size.width * ratio).toInt(), (info.size.height * ratio).toInt())
                }
                // Software, so createBitmap can crop it.
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= DECODE_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("Unreadable image")
    }

    private companion object {
        const val MAX_SIDE = 512
        const val DECODE_SIDE = 1024
        const val JPEG_QUALITY = 90
    }
}
