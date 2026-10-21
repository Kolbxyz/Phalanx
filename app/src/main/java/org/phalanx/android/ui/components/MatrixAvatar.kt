package org.phalanx.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import org.phalanx.android.data.model.MediaRef
import org.phalanx.android.data.model.MediaRequest

/**
 * A server avatar laid over the name's initial: the initial shows while the image loads, and stays
 * if there is none or it fails, so the slot never flickers or jumps.
 */
@Composable
internal fun MatrixAvatar(
    name: String,
    media: MediaRef?,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    textStyle: TextStyle = MaterialTheme.typography.titleSmall
) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    Box(modifier.size(size)) {
        InitialAvatar(
            name = name,
            shape = shape,
            size = size,
            container = container,
            content = content,
            textStyle = textStyle
        )
        if (media != null) {
            // Decorative: the name is always beside the avatar or in its button's description.
            AsyncImage(
                model = MediaRequest(media, px),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().clip(shape)
            )
        }
    }
}
