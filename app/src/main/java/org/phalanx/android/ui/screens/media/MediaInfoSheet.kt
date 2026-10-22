package org.phalanx.android.ui.screens.media

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.ui.components.ModalSheet
import org.phalanx.android.ui.theme.Symbols

/**
 * What the viewer's info button offers on a photo or video: other ways to use it, then what is
 * known about the file. [onJumpTo] is null while the message is still sending.
 */
@Composable
internal fun MediaInfoSheet(
    entry: TimelineEntry.Message,
    onDismiss: () -> Unit,
    onOpenWith: () -> Unit,
    onForward: (() -> Unit)?,
    onJumpTo: (() -> Unit)?
) {
    ModalSheet(onDismissRequest = onDismiss) {
        Action(Symbols.OpenInNew, R.string.media_open_with, onOpenWith)
        onForward?.let { Action(Symbols.Forward, R.string.action_forward, it) }
        onJumpTo?.let { Action(Symbols.Chat, R.string.media_jump_to_message, it) }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        detailsOf(entry).forEach { (label, value) ->
            ListItem(
                overlineContent = { Text(stringResource(label)) },
                headlineContent = { Text(value) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Action(icon: ImageVector, @StringRes label: Int, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(label)) },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick)
    )
}

/** Only what the message says; nothing is shown for what its sender left out. */
@Composable
private fun detailsOf(entry: TimelineEntry.Message): List<Pair<Int, String>> {
    val context = LocalContext.current
    val body = entry.body
    val file = body.fileOf() ?: return emptyList()
    val (width, height) = when (body) {
        is MessageBody.Image -> body.width to body.height
        is MessageBody.Video -> body.width to body.height
        else -> null to null
    }
    val size = when (body) {
        is MessageBody.Image -> body.sizeBytes
        is MessageBody.Video -> body.sizeBytes
        else -> null
    }
    val duration = (body as? MessageBody.Video)?.durationMillis
    val date = DateUtils.formatDateTime(
        context,
        entry.timestampMillis,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_YEAR
    )
    return listOfNotNull(
        R.string.media_info_name to file.filename,
        file.mimeType?.let { R.string.media_info_type to it },
        size?.let { R.string.media_info_size to Formatter.formatShortFileSize(context, it) },
        if (width != null && height != null) {
            R.string.media_info_dimensions to stringResource(R.string.media_info_dimensions_value, width, height)
        } else {
            null
        },
        duration?.let { R.string.media_info_duration to DateUtils.formatElapsedTime(it / MILLIS_PER_SECOND) },
        R.string.media_info_sender to entry.senderName,
        R.string.media_info_date to date
    )
}

private const val MILLIS_PER_SECOND = 1000L
