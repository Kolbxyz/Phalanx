package org.phalanx.android.ui.screens.room

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.data.model.ScheduledMessage

/** This room's messages waiting to be sent, above the composer, each with "send now" and cancel. */
@Composable
internal fun ScheduledStrip(
    messages: List<ScheduledMessage>,
    onSendNow: (String) -> Unit,
    onCancel: (String) -> Unit
) {
    Column(
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        messages.forEach { message -> ScheduledRow(message, onSendNow, onCancel) }
    }
}

@Composable
private fun ScheduledRow(message: ScheduledMessage, onSendNow: (String) -> Unit, onCancel: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val failed = message.failed
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (failed) colors.errorContainer else colors.secondaryContainer,
        contentColor = if (failed) colors.onErrorContainer else colors.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(if (failed) Icons.Rounded.ErrorOutline else Icons.Rounded.Schedule, contentDescription = null)
            Column(Modifier.weight(1f)) {
                val time = DateUtils.formatDateTime(
                    context,
                    message.sendAtMillis,
                    DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_ALL
                )
                Text(
                    text = if (failed) {
                        stringResource(R.string.schedule_failed)
                    } else {
                        stringResource(R.string.schedule_at, time)
                    },
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = { onSendNow(message.id) }) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = stringResource(R.string.schedule_send_now))
            }
            IconButton(onClick = { onCancel(message.id) }) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.schedule_cancel))
            }
        }
    }
}
