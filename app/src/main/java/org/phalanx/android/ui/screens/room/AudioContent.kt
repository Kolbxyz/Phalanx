package org.phalanx.android.ui.screens.room

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.phalanx.android.R
import org.phalanx.android.data.model.MediaRef
import org.phalanx.android.data.model.MessageBody
import java.io.IOException

private const val PROGRESS_TICK_MILLIS = 100L
private const val MIN_BAR = 0.08f
private const val BARS = 40
private const val FLAT_LEVEL = 0.25f

/** Play/pause, the waveform filling up as it plays, and the duration. */
@Composable
internal fun AudioContent(
    body: MessageBody.Audio,
    resolve: suspend (MediaRef, String, String?) -> Uri?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }

    DisposableEffect(Unit) { onDispose { player?.release() } }
    // Only ticks while sound is actually playing.
    LaunchedEffect(playing) {
        while (playing) {
            player?.let { if (it.duration > 0) progress = it.currentPosition.toFloat() / it.duration }
            delay(PROGRESS_TICK_MILLIS)
        }
    }

    fun toggle() {
        val current = player
        when {
            current != null && playing -> current.pause().also { playing = false }
            current != null -> current.start().also { playing = true }
            !loading -> scope.launch {
                loading = true
                val uri = resolve(body.media, body.filename, body.mimeType)
                player = uri?.let {
                    prepare(context, it, body.isVoice, onReady = { playing = true }, onDone = {
                        playing = false
                        progress = 0f
                    })
                }
                loading = false
            }
        }
    }

    Row(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalIconButton(onClick = ::toggle) {
            when {
                loading -> LoadingIndicator(Modifier.size(24.dp))
                playing -> Icon(Icons.Rounded.Pause, contentDescription = stringResource(R.string.timeline_pause))
                else -> Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.timeline_play))
            }
        }
        // Every audio message gets the same bars; senders that include no waveform get a flat one,
        // so a message looks the same whichever client sent it.
        val bars = remember(body.waveform) {
            body.waveform?.takeIf { it.isNotEmpty() }?.let { downsample(it, BARS) } ?: List(BARS) { FLAT_LEVEL }
        }
        Waveform(bars, progress, Modifier.width(140.dp).height(32.dp))
        Text(
            text = body.durationMillis?.let(::formatDuration) ?: stringResource(kindLabel(body)),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

/** Starts playing as soon as it is ready. Null when the file cannot be read. */
private fun prepare(
    context: Context,
    uri: Uri,
    speech: Boolean,
    onReady: () -> Unit,
    onDone: () -> Unit
): MediaPlayer? {
    val player = MediaPlayer()
    return try {
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(if (speech) AudioAttributes.CONTENT_TYPE_SPEECH else AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        player.setDataSource(context, uri)
        player.setOnPreparedListener {
            it.start()
            onReady()
        }
        player.setOnCompletionListener { onDone() }
        player.prepareAsync()
        player
    } catch (_: IOException) {
        player.release()
        null
    }
}

@Composable
internal fun Waveform(samples: List<Float>, progress: Float, modifier: Modifier = Modifier) {
    val played = LocalContentColor.current
    val rest = played.copy(alpha = 0.35f)
    Canvas(modifier) {
        val gap = 2.dp.toPx()
        val count = samples.size
        val barWidth = ((size.width - gap * (count - 1)) / count).coerceAtLeast(1f)
        samples.forEachIndexed { index, sample ->
            val barHeight = size.height * sample.coerceAtLeast(MIN_BAR)
            val x = index * (barWidth + gap)
            drawRoundRect(
                color = if (index.toFloat() / count < progress) played else rest,
                topLeft = Offset(x, (size.height - barHeight) / 2),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2)
            )
        }
    }
}

/** Senders may send hundreds of samples; the bubble has room for [count] bars, each the loudest of its slice. */
private fun downsample(samples: List<Float>, count: Int): List<Float> {
    if (samples.size <= count) return samples
    return List(count) { bar ->
        val from = bar * samples.size / count
        val to = ((bar + 1) * samples.size / count).coerceAtLeast(from + 1)
        samples.subList(from, to).max()
    }
}
