package org.phalanx.android.ui.screens.room

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.phalanx.android.R
import org.phalanx.android.data.model.OutgoingMedia

private val TileSize = 76.dp
private val TileShape = RoundedCornerShape(16.dp)

/** The files waiting to be sent with the next message, each removable before it goes. */
@Composable
internal fun AttachmentStrip(attachments: List<PendingAttachment>, onRemove: (String) -> Unit) {
    val motion = MaterialTheme.motionScheme
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp)
    ) {
        items(attachments, key = { it.id }) { attachment ->
            Box(
                Modifier.animateItem(
                    fadeInSpec = motion.defaultEffectsSpec(),
                    placementSpec = motion.defaultSpatialSpec(),
                    fadeOutSpec = motion.defaultEffectsSpec()
                )
            ) {
                Tile(attachment.media)
                FilledTonalIconButton(
                    onClick = { onRemove(attachment.id) },
                    modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(24.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.composer_remove_attachment),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun Tile(media: OutgoingMedia?) {
    Surface(
        shape = TileShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.size(TileSize)
    ) {
        when (media) {
            null -> Box(contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(32.dp)) }
            is OutgoingMedia.Image -> Preview(media)
            is OutgoingMedia.Video -> Box(contentAlignment = Alignment.Center) {
                media.thumbnail?.let { Preview(it) }
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            }
            is OutgoingMedia.Document, is OutgoingMedia.Voice -> Column(
                modifier = Modifier.padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.AutoMirrored.Rounded.InsertDriveFile, contentDescription = null)
                Text(
                    text = media.file.name,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun Preview(image: OutgoingMedia.Image) {
    AsyncImage(
        model = image.file,
        contentDescription = image.file.name,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
    )
}
