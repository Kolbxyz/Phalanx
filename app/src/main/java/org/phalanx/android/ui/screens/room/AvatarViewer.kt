package org.phalanx.android.ui.screens.room

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import org.phalanx.android.R
import org.phalanx.android.data.model.MediaRef
import org.phalanx.android.data.model.MediaRequest
import org.phalanx.android.ui.screens.media.SwipeToDismiss
import org.phalanx.android.ui.theme.Symbols

/** Someone's profile picture at full size, over everything; swiped away or closed like the media viewer. */
@Composable
internal fun AvatarViewer(avatar: MediaRef, name: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        SwipeToDismiss(enabled = true, background = Color.Black, onDismiss = onDismiss) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                AsyncImage(
                    model = MediaRequest(avatar),
                    contentDescription = name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth()
                )
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopStart).safeDrawingPadding()) {
                    Icon(
                        Symbols.Close,
                        contentDescription = stringResource(R.string.media_viewer_close),
                        tint = Color.White
                    )
                }
            }
        }
    }
}
