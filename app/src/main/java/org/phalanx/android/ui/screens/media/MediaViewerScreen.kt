package org.phalanx.android.ui.screens.media

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.phalanx.android.R
import org.phalanx.android.data.model.MediaRequest
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.ui.navigation.sharedMedia

private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f

// The size the bubble asked for: already in the memory cache, so something shows at once.
private const val BUBBLE_THUMBNAIL_PX = 800

@Composable
fun MediaViewerScreen(onClose: () -> Unit, viewModel: MediaViewerViewModel = hiltViewModel()) {
    val media by viewModel.media.collectAsStateWithLifecycle()
    val videoUri by viewModel.videoUri.collectAsStateWithLifecycle()
    val videoFailed by viewModel.videoFailed.collectAsStateWithLifecycle()
    val image = media as? MessageBody.Image
    val scope = rememberCoroutineScope()
    val motion = MaterialTheme.motionScheme
    val zoom = remember { Animatable(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoomChange, panChange, _ ->
        scope.launch { zoom.snapTo((zoom.value * zoomChange).coerceIn(1f, MAX_ZOOM)) }
        pan = if (zoom.value > 1f) pan + panChange else Offset.Zero
    }

    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            image?.let { body ->
                val description = body.caption ?: stringResource(R.string.timeline_kind_image)
                Box(
                    Modifier
                        .fillMaxSize()
                        .sharedMedia("media-${viewModel.entryId}")
                        .transformable(transform)
                        .pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = {
                                scope.launch {
                                    val target = if (zoom.value > 1f) 1f else DOUBLE_TAP_ZOOM
                                    if (target == 1f) pan = Offset.Zero
                                    zoom.animateTo(target, motion.defaultSpatialSpec())
                                }
                            })
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
            (media as? MessageBody.Video)?.let { video ->
                Box(Modifier.fillMaxSize().sharedMedia("media-${viewModel.entryId}"), Alignment.Center) {
                    val uri = videoUri
                    when {
                        uri != null -> VideoPlayer(uri)
                        videoFailed -> Text(
                            stringResource(R.string.timeline_open_failed),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        else -> {
                            // The bubble's thumbnail while the video downloads and decrypts.
                            video.thumbnail?.let {
                                AsyncImage(
                                    model = MediaRequest(it, BUBBLE_THUMBNAIL_PX),
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            LoadingIndicator(Modifier.size(64.dp))
                        }
                    }
                }
            }
            FilledTonalIconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(8.dp)
            ) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.media_viewer_close))
            }
        }
    }
}
