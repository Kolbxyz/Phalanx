package org.phalanx.android.ui.screens.media

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.phalanx.android.R
import org.phalanx.android.data.model.MediaRequest
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.ui.navigation.sharedMedia

private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f

// The size the bubble asked for: already in the memory cache, so something shows at once.
private const val BUBBLE_THUMBNAIL_PX = 800

/** One page of the viewer. [onZoomed] tells the pager to stop swiping while the picture is zoomed in. */
@Composable
internal fun MediaPage(
    entry: TimelineEntry.Message,
    video: VideoFile?,
    active: Boolean,
    onTap: () -> Unit,
    onZoomed: (Boolean) -> Unit
) {
    when (val body = entry.body) {
        is MessageBody.Image -> ZoomableImage(entry.id, body, onTap, onZoomed)
        is MessageBody.Video -> VideoPage(entry.id, body, video, active, onTap)
        else -> Unit
    }
}

@Composable
private fun ZoomableImage(id: String, body: MessageBody.Image, onTap: () -> Unit, onZoomed: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    val motion = MaterialTheme.motionScheme
    val zoom = remember { Animatable(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoomChange, panChange, _ ->
        scope.launch { zoom.snapTo((zoom.value * zoomChange).coerceIn(1f, MAX_ZOOM)) }
        pan = if (zoom.value > 1f) pan + panChange else Offset.Zero
    }
    LaunchedEffect(zoom) { snapshotFlow { zoom.value > 1f }.collect(onZoomed) }
    val description = body.caption?.text ?: stringResource(R.string.timeline_kind_image)
    Box(
        Modifier
            .fillMaxSize()
            .sharedMedia("media-$id")
            // Only while zoomed, so an unzoomed drag goes to the pager.
            .transformable(transform, canPan = { zoom.value > 1f })
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        scope.launch {
                            val target = if (zoom.value > 1f) 1f else DOUBLE_TAP_ZOOM
                            if (target == 1f) pan = Offset.Zero
                            zoom.animateTo(target, motion.defaultSpatialSpec())
                        }
                    }
                )
            }
            .graphicsLayer {
                scaleX = zoom.value
                scaleY = zoom.value
                translationX = pan.x
                translationY = pan.y
            }
    ) {
        // The bubble's thumbnail first, the full image on top once it has loaded.
        AsyncImage(
            model = MediaRequest(body.thumbnail ?: body.media, BUBBLE_THUMBNAIL_PX),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
        AsyncImage(
            model = MediaRequest(body.media),
            contentDescription = description,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/** Only the page in view plays; its neighbours show their thumbnail. */
@Composable
private fun VideoPage(id: String, body: MessageBody.Video, file: VideoFile?, active: Boolean, onTap: () -> Unit) {
    Box(
        Modifier.fillMaxSize().sharedMedia("media-$id").pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) },
        Alignment.Center
    ) {
        when {
            active && file is VideoFile.Ready -> VideoPlayer(file.uri)
            file is VideoFile.Failed -> Text(
                stringResource(R.string.timeline_open_failed),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> {
                body.thumbnail?.let {
                    AsyncImage(
                        model = MediaRequest(it, BUBBLE_THUMBNAIL_PX),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (active) LoadingIndicator(Modifier.size(64.dp))
            }
        }
    }
}
