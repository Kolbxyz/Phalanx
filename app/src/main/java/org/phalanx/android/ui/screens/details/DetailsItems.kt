package org.phalanx.android.ui.screens.details

import android.content.Context
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.phalanx.android.data.model.MediaRequest
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.ui.components.SegmentedRow
import org.phalanx.android.ui.components.pressShrink
import org.phalanx.android.ui.screens.room.summaryOf
import org.phalanx.android.ui.screens.room.videoPreview
import org.phalanx.android.ui.theme.Symbols

private const val TILE_PX = 360
private const val PRESSED_TILE = 0.95f
private const val SECONDS = 60L
private val TileShape = RoundedCornerShape(12.dp)

/** One pinned message: who sent it and what it says, in a segmented strip. */
@Composable
internal fun PinnedRow(message: TimelineEntry.Message, shape: Shape, onClick: () -> Unit) {
    SegmentedRow(
        headline = summaryOf(message.body),
        supporting = message.senderName,
        shape = shape,
        interaction = Modifier.clickable(onClick = onClick),
        leading = { Icon(Symbols.PushPin, contentDescription = null) }
    )
}

/** A square thumbnail; a video's carries a play mark and how long it runs. It shrinks a little when pressed. */
@Composable
internal fun MediaTile(
    message: TimelineEntry.Message,
    modifier: Modifier,
    onOpen: (TimelineEntry.Message) -> Unit,
    shape: Shape = TileShape
) {
    val interaction = remember { MutableInteractionSource() }
    val body = message.body
    val thumbnail = when (body) {
        is MessageBody.Image -> MediaRequest(body.thumbnail ?: body.media, TILE_PX)
        is MessageBody.Video -> videoPreview(body, TILE_PX)
        else -> null
    }
    Surface(
        onClick = { onOpen(message) },
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        interactionSource = interaction,
        modifier = modifier.aspectRatio(1f).pressShrink(interaction, PRESSED_TILE)
    ) {
        Box(contentAlignment = Alignment.Center) {
            thumbnail?.let {
                AsyncImage(
                    model = it,
                    contentDescription = summaryOf(body),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (body is MessageBody.Video) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(Symbols.PlayArrow, contentDescription = null, modifier = Modifier.size(28.dp))
                }
                body.durationMillis?.let { millis ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                    ) {
                        Text(
                            durationOf(millis),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
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
        supporting = fileMeta(message, LocalContext.current),
        shape = shape,
        interaction = Modifier.clickable(onClick = onClick),
        leading = {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        if (body is MessageBody.Audio) Symbols.GraphicEq else Symbols.InsertDriveFile,
                        contentDescription = null
                    )
                }
            }
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

/** "0:12": a video's length as players show it. */
private fun durationOf(millis: Long): String {
    val seconds = millis / MILLIS
    return "%d:%02d".format(seconds / SECONDS, seconds % SECONDS)
}

/** "PDF · 184 KB · 9 Nov": what kind of file, how big, and when it was shared. */
private fun fileMeta(message: TimelineEntry.Message, context: Context): String {
    val body = message.body
    val (name, mime, size) = when (body) {
        is MessageBody.File -> Triple(body.filename, body.mimeType, body.sizeBytes)
        is MessageBody.Audio -> Triple(body.filename, body.mimeType, null)
        else -> Triple(null, null, null)
    }
    val kind = name?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() && it.length <= MAX_EXTENSION }
        ?: mime?.substringAfter('/')
    val sharedAt = Instant.ofEpochMilli(message.timestampMillis).atZone(ZoneId.systemDefault())
    val date = DateTimeFormatter.ofPattern("d MMM").format(sharedAt)
    return listOfNotNull(
        kind?.uppercase(),
        size?.let { Formatter.formatShortFileSize(context, it) },
        date
    ).joinToString(" · ")
}

private const val MILLIS = 1_000L
private const val MAX_EXTENSION = 5
