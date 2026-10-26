package org.phalanx.android.ui.share

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.core.content.IntentCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.phalanx.android.R
import org.phalanx.android.ui.screens.room.ForwardSheet

/** While another app's share waits, asks which conversation it goes to, then opens it there. */
@Composable
fun SharePicker(onPicked: (roomId: String) -> Unit, viewModel: ShareViewModel = hiltViewModel()) {
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    if (pending == null) return
    val rooms by viewModel.rooms.collectAsStateWithLifecycle()
    ForwardSheet(
        rooms = rooms,
        onPick = { roomId ->
            viewModel.sendTo(roomId)
            onPicked(roomId)
        },
        onDismiss = viewModel::dismiss,
        title = R.string.share_title
    )
}

/** The text and files of an ACTION_SEND or ACTION_SEND_MULTIPLE intent; null for any other. */
fun sharedContent(intent: Intent): SharedContent? {
    val uris: List<Uri> = when (intent.action) {
        Intent.ACTION_SEND ->
            listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
        Intent.ACTION_SEND_MULTIPLE ->
            IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        else -> return null
    }
    val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
    return if (text.isNullOrBlank() && uris.isEmpty()) null else SharedContent(text, uris)
}
