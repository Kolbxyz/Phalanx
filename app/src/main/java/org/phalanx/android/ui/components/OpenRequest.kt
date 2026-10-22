package org.phalanx.android.ui.components

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext

/** A file ready to hand to another app. */
data class OpenRequest(val uri: Uri, val mimeType: String?)

/**
 * Hands [request] to whichever app opens its type, with read access to that one file only. With
 * [chooser], the user picks the app every time instead of their default.
 */
@Composable
internal fun OpenFileEffect(
    request: OpenRequest?,
    onOpened: () -> Unit,
    chooser: Boolean = false,
    onNoApp: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(request) {
        if (request == null) return@LaunchedEffect
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(request.uri, request.mimeType ?: "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // The grant only reaches the chosen app through a chooser when the URI is also clip data.
        if (chooser) intent.clipData = ClipData.newRawUri(null, request.uri)
        try {
            context.startActivity(if (chooser) Intent.createChooser(intent, null) else intent)
        } catch (_: ActivityNotFoundException) {
            onNoApp()
        }
        onOpened()
    }
}
