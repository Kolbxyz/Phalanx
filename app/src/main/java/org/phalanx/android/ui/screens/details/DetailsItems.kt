package org.phalanx.android.ui.screens.details

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.phalanx.android.data.model.MediaRequest
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.ui.components.SegmentedRow
import org.phalanx.android.ui.screens.room.summaryOf

private const val TILE_PX = 360
private val TileShape = RoundedCornerShape(12.dp)

/** One pinned message: who sent it and what it says, in a segmented strip. */
@Composable
internal fun PinnedRow(message: TimelineEntry.Message, shape: Shape, onClick: () -> Unit) {
    SegmentedRow(
        headline = summaryOf(message.body),
        supporting = message.senderName,
        shape = shape,
        interaction = Modifier.clickable(onClick = onClick),
        leading = { Icon(Icons.Rounded.PushPin, contentDescription = null) }
    )
}

/** A row of square thumbnails; short rows keep their cells' size instead of stretching. */
@Composable
internal fun MediaGridRow(items: List<TimelineEntry.Message>, columns: Int, onOpen: (TimelineEntry.Message) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { message -> MediaTile(message, Modifier.weight(1f), onOpen) }
        repeat(columns - items.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun MediaTile(message: TimelineEntry.Message, modifier: Modifier, onOpen: (TimelineEntry.Message) -> Unit) {
    val body = message.body
    val thumbnail = when (body) {
        is MessageBody.Image -> body.thumbnail ?: body.media
        is MessageBody.Video -> body.thumbnail
        else -> null
    }
    Surface(
        shape = TileShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.aspectRatio(1f).clickable { onOpen(message) }
    ) {
        Box(contentAlignment = Alignment.Center) {
            thumbnail?.let {
                AsyncImage(
                    model = MediaRequest(it, TILE_PX),
                    contentDescription = summaryOf(body),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (body is MessageBody.Video) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

/** A shared file or audio clip; opens in the app that handles its type. */
@Composable
internal fun FileRow(message: TimelineEntry.Message, shape: Shape, onClick: () -> Unit) {
    val body = message.body
    SegmentedRow(
        headline = when (body) {
            is MessageBody.File -> body.filename
            is MessageBody.Audio -> body.filename
            else -> summaryOf(body)
        },
        supporting = message.senderName,
        shape = shape,
        interaction = Modifier.clickable(onClick = onClick),
        leading = {
            Icon(
                if (body is MessageBody.Audio) Icons.Rounded.GraphicEq else Icons.AutoMirrored.Rounded.InsertDriveFile,
                contentDescription = null
            )
        }
    )
}

/** What an empty section says instead of its rows. */
@Composable
internal fun EmptyNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth()
    )
}
