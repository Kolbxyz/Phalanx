package org.phalanx.android.ui.screens.room

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.phalanx.android.R
import org.phalanx.android.data.model.MediaRef
import org.phalanx.android.data.model.MediaRequest
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.ui.theme.Symbols

internal val MediaWidth = 260.dp
private val StickerSize = 160.dp

private const val GIF_MIME = "image/gif"

// Server thumbnails at this size stay sharp at the bubble's width on any phone screen.
private const val THUMBNAIL_PX = 800

// Discord's GIFs weigh a few megabytes at most.
private const val FRAME_MAX_BYTES = 8L * 1024 * 1024
private const val STICKER_PX = 512
private const val MIN_RATIO = 0.6f
private const val MAX_RATIO = 1.8f

/** The bubble keeps its final size while the image loads, so nothing below it jumps. */
@Composable
internal fun ImageContent(
    body: MessageBody.Image,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val description = body.caption?.text ?: stringResource(kindLabel(body))
    if (body.isSticker) {
        AsyncImage(
            model = MediaRequest(body.media, STICKER_PX),
            contentDescription = description,
            contentScale = ContentScale.Fit,
            modifier = modifier.size(StickerSize)
        )
        return
    }
    val autoLoad = LocalTimelinePrefs.current.autoLoadMedia
    var load by rememberSaveable(body.media) { mutableStateOf(autoLoad) }
    val frame = modifier
        .width(MediaWidth)
        .aspectRatio(ratioOf(body.width, body.height))
        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    if (!load) {
        TapToLoad(description, frame, onLoad = { load = true }, onLongPress)
        return
    }
    // A GIF's server thumbnail is a still frame; the file itself is small enough to play in place.
    val request = if (body.mimeType == GIF_MIME) {
        MediaRequest(body.media)
    } else {
        MediaRequest(body.thumbnail ?: body.media, THUMBNAIL_PX)
    }
    AsyncImage(
        model = request,
        contentDescription = description,
        contentScale = ContentScale.Crop,
        modifier = frame.combinedClickable(onClick = onOpen, onLongClick = onLongPress)
    )
}

/** Stands in for a picture until the user asks for it, when media isn't loaded automatically. */
@Composable
private fun TapToLoad(description: String, modifier: Modifier, onLoad: () -> Unit, onLongPress: () -> Unit) {
    Box(modifier.combinedClickable(onClick = onLoad, onLongClick = onLongPress), contentAlignment = Alignment.Center) {
        Surface(shape = MaterialShapes.Cookie9Sided.toShape(), color = MaterialTheme.colorScheme.secondaryContainer) {
            Icon(
                Symbols.Download,
                contentDescription = stringResource(R.string.timeline_load_media, description),
                modifier = Modifier.padding(14.dp).size(28.dp)
            )
        }
    }
}

@Composable
internal fun VideoContent(
    body: MessageBody.Video,
    actions: MessageActions,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(MediaWidth)
            .aspectRatio(ratioOf(body.width, body.height))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress),
        contentAlignment = Alignment.Center
    ) {
        val load = LocalTimelinePrefs.current.autoLoadMedia
        videoPreview(body, THUMBNAIL_PX)?.takeIf { load }?.let {
            AsyncImage(
                model = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (body.isGif) {
            // Plays over its still frame like an image would, with nothing to press or time.
            if (load) GifVideo(body, actions)
            return@Box
        }
        Surface(shape = MaterialShapes.Circle.toShape(), color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(
                Symbols.PlayArrow,
                contentDescription = stringResource(R.string.timeline_play),
                modifier = Modifier.padding(12.dp).size(32.dp)
            )
        }
        body.durationMillis?.let {
            Text(
                text = formatDuration(it),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.7f), CircleShape)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
internal fun FileContent(
    body: MessageBody.File,
    onOpen: (MediaRef, String, String?) -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .combinedClickable(onLongClick = onLongPress) { onOpen(body.media, body.filename, body.mimeType) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = MaterialShapes.Cookie4Sided.toShape(), color = MaterialTheme.colorScheme.secondaryContainer) {
            Icon(
                Symbols.InsertDriveFile,
                contentDescription = null,
                modifier = Modifier.padding(10.dp)
            )
        }
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = body.filename,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            body.sizeBytes?.let {
                Text(Formatter.formatShortFileSize(context, it), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Opens the user's maps app on the shared point. */
@Composable
internal fun LocationContent(body: MessageBody.Location, onLongPress: () -> Unit, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Row(
        modifier = modifier
            .combinedClickable(onLongClick = onLongPress) { uriHandler.openUri(body.geoUri) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Symbols.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
            text = body.description ?: stringResource(R.string.timeline_kind_location),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

/**
 * The server's thumbnail, or else the first frame of a short video (Discord sends its GIFs as
 * MP4s without one). Longer videos are not downloaded whole just for a still.
 */
internal fun videoPreview(body: MessageBody.Video, px: Int): MediaRequest? = when {
    body.thumbnail != null -> MediaRequest(body.thumbnail, px)
    (body.sizeBytes ?: Long.MAX_VALUE) <= FRAME_MAX_BYTES -> MediaRequest(body.media, px, videoFrame = true)
    else -> null
}

private fun ratioOf(width: Int?, height: Int?): Float {
    if (width == null || height == null) return 1f
    if (width <= 0 || height <= 0) return 1f
    return (width.toFloat() / height).coerceIn(MIN_RATIO, MAX_RATIO)
}

internal fun formatDuration(millis: Long): String {
    val seconds = millis / MILLIS_PER_SECOND
    return "%d:%02d".format(seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)
}

private const val MILLIS_PER_SECOND = 1000
private const val SECONDS_PER_MINUTE = 60
