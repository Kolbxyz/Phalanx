package org.phalanx.android.core.media

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.graphics.scale
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Turns a picked image into an avatar upload: centre-cropped square, at most [MAX_SIDE] px, JPEG.
 * Re-encoding is the point as much as the size: the original file's EXIF (GPS position, camera,
 * time taken) never leaves the device, because only decoded pixels are written back out.
 */
@Singleton
class AvatarEncoder @Inject constructor(@ApplicationContext private val context: Context) {
    suspend fun encode(uri: Uri): ByteArray = withContext(Dispatchers.Default) {
        val decoded = context.contentResolver.decodeScaled(uri, DECODE_SIDE)
        val side = min(decoded.width, decoded.height)
        val square = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side)
        val target = min(side, MAX_SIDE)
        val scaled = if (target == side) square else square.scale(target, target)
        ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
    }

    private companion object {
        const val MAX_SIDE = 512
        const val DECODE_SIDE = 1024
        const val JPEG_QUALITY = 90
    }
}
