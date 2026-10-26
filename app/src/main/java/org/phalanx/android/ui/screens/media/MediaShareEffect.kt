package org.phalanx.android.ui.screens.media

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import org.phalanx.android.ui.components.OpenRequest

/** Hands [request] to the system share sheet, with read access to that one file only. */
@Composable
internal fun ShareEffect(request: OpenRequest?, onShared: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(request) {
        if (request == null) return@LaunchedEffect
        val send = Intent(Intent.ACTION_SEND)
            .setType(request.mimeType ?: "*/*")
            .putExtra(Intent.EXTRA_STREAM, request.uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // The grant only reaches the chosen app through the chooser when the URI is also clip data.
        send.clipData = ClipData.newRawUri(null, request.uri)
        try {
            context.startActivity(Intent.createChooser(send, null))
        } catch (_: ActivityNotFoundException) {
            // A chooser always resolves; nothing to tell the user if a broken system has none.
        }
        onShared()
    }
}
