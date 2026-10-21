package org.phalanx.android.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext

/** A file ready to hand to another app. */
data class OpenRequest(val uri: Uri, val mimeType: String?)

/** Hands [request] to whichever app opens its type, with read access to that one file only. */
@Composable
internal fun OpenFileEffect(request: OpenRequest?, onOpened: () -> Unit, onNoApp: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(request) {
        if (request == null) return@LaunchedEffect
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(request.uri, request.mimeType ?: "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            onNoApp()
        }
        onOpened()
    }
}
