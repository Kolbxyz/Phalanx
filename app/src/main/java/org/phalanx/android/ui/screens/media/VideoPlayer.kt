package org.phalanx.android.ui.screens.media

import android.net.Uri
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import org.phalanx.android.R
import org.phalanx.android.ui.screens.room.formatDuration

private const val TICK_MILLIS = 250L
private const val HIDE_CONTROLS_MILLIS = 2_500L

/**
 * Plays a decrypted video from the app's cache with the platform's player: no extra library, no
 * other app. Tapping shows or hides the controls; they hide by themselves while it plays.
 */
@Composable
internal fun VideoPlayer(uri: Uri, modifier: Modifier = Modifier) {
    var view by remember { mutableStateOf<VideoView?>(null) }
    var playing by remember { mutableStateOf(false) }
    var duration by remember { mutableIntStateOf(0) }
    var position by remember { mutableFloatStateOf(0f) }
    var controls by remember { mutableStateOf(true) }

    // Reads the position only while playing and on screen; stops with the player.
    LaunchedEffect(playing) {
        while (playing) {
            view?.let { position = it.currentPosition.toFloat() }
            delay(TICK_MILLIS)
        }
    }
    LaunchedEffect(playing, controls) {
        if (playing && controls) {
            delay(HIDE_CONTROLS_MILLIS)
            controls = false
        }
    }
    DisposableEffect(Unit) { onDispose { view?.stopPlayback() } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                controls = !controls
            },
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { context ->
                VideoView(context).apply {
                    setOnPreparedListener { player ->
                        duration = player.duration
                        player.start()
                        playing = true
                    }
                    setOnCompletionListener {
                        playing = false
                        controls = true
                        position = duration.toFloat()
                    }
                    setVideoURI(uri)
                    view = this
                }
            }
        )
        AnimatedVisibility(
            visible = controls,
            enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec())
        ) {
            Box(Modifier.fillMaxSize()) {
                FilledIconButton(
                    onClick = {
                        val player = view ?: return@FilledIconButton
                        if (playing) player.pause() else player.start()
                        playing = !playing
                    },
                    modifier = Modifier.align(Alignment.Center).size(72.dp)
                ) {
                    Icon(
                        if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(if (playing) R.string.timeline_pause else R.string.timeline_play),
                        modifier = Modifier.size(36.dp)
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f),
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(formatDuration(position.toLong()), style = MaterialTheme.typography.labelLarge)
                        Slider(
                            value = position,
                            onValueChange = {
                                position = it
                                view?.seekTo(it.toInt())
                            },
                            valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
                            modifier = Modifier.weight(1f)
                        )
                        Text(formatDuration(duration.toLong()), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
