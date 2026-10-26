package org.phalanx.android.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp

/** [image] cut to [shape] when there is one; otherwise the name's first letter on a tinted shape. */
@Composable
internal fun InitialAvatar(
    name: String,
    shape: Shape,
    size: Dp,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    image: ImageBitmap? = null,
    textStyle: TextStyle = MaterialTheme.typography.titleLarge
) {
    Surface(shape = shape, color = container, contentColor = content, modifier = modifier.size(size)) {
        if (image != null) {
            // Decorative: the name is always next to the avatar or in its button's description.
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(text = initialOf(name), style = textStyle)
            }
        }
    }
}

/** The first letter or digit, skipping the `@`, `#` and `!` sigils Matrix IDs start with. */
internal fun initialOf(name: String): String =
    name.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "#"

/** Image bytes from the server, or null when there are none or they are not an image. */
internal fun decodeAvatar(bytes: ByteArray?): ImageBitmap? =
    bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }?.asImageBitmap()
