package org.phalanx.android.ui.screens.room

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import org.phalanx.android.data.model.MessageBody
import java.io.IOException

/**
 * A bridged GIF playing in its bubble: silent, looping, and only while at least half of it is on
 * screen and the room is in front. Off screen its player is released, and the still frame under it
 * shows again; the file comes from the same decrypted cache as the viewer's.
 */
@Composable
internal fun GifVideo(body: MessageBody.Video, actions: MessageActions, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    val resumed by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val play = visible && resumed.isAtLeast(Lifecycle.State.RESUMED)
    val uri by produceState<Uri?>(null, body.media, play) {
        if (play && value == null) value = actions.resolveFile(body.media, body.filename, body.mimeType)
    }
    // Always laid out, player or not, so it keeps reporting whether it is on screen.
    Box(modifier.fillMaxSize().onVisibilityChanged(minFractionVisible = HALF) { visible = it }) {
        val source = uri.takeIf { play } ?: return@Box
        var rendering by remember(source) { mutableStateOf(false) }
        AndroidView(
            factory = { context ->
                TextureView(context).apply { surfaceTextureListener = LoopingPlayer(this, source) { rendering = true } }
            },
            onRelease = { (it.surfaceTextureListener as? LoopingPlayer)?.release() },
            // Hidden until its first frame, so the still under it never flashes black.
            modifier = Modifier.fillMaxSize().alpha(if (rendering) 1f else 0f)
        )
    }
}

private class LoopingPlayer(
    private val view: TextureView,
    private val uri: Uri,
    private val onFirstFrame: () -> Unit
) : TextureView.SurfaceTextureListener {
    private var player: MediaPlayer? = null
    private var surface: Surface? = null

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        val target = Surface(texture).also { surface = it }
        val created = MediaPlayer()
        try {
            created.setDataSource(view.context, uri)
        } catch (_: IOException) {
            // Unreadable: the still frame stays, as for a video that was never auto-loaded.
            created.release()
            return
        }
        created.setSurface(target)
        created.isLooping = true
        created.setVolume(0f, 0f)
        created.setOnInfoListener { _, what, _ ->
            if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) onFirstFrame()
            false
        }
        created.setOnErrorListener { _, _, _ -> true }
        created.setOnPreparedListener { it.start() }
        created.prepareAsync()
        player = created
    }

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        release()
        return true
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

    fun release() {
        player?.release()
        player = null
        surface?.release()
        surface = null
    }
}

private const val HALF = 0.5f
