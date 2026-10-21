package org.phalanx.android.ui.screens.room

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.model.SendState
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.data.model.UnsupportedKind
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val BubbleRadius = 20.dp
private val TailRadius = 6.dp

@Composable
internal fun MessageBubble(message: TimelineEntry.Message, showSender: Boolean, modifier: Modifier = Modifier) {
    val own = message.isOwn
    // The corner that points at the sender is the tight one.
    val shape = RoundedCornerShape(
        topStart = BubbleRadius,
        topEnd = BubbleRadius,
        bottomStart = if (own) BubbleRadius else TailRadius,
        bottomEnd = if (own) TailRadius else BubbleRadius
    )
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (own) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = shape,
            color = if (own) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (own) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                if (showSender && !own) {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                BodyText(message.body)
                Footer(message, modifier = Modifier.align(Alignment.End))
            }
        }
    }
}

@Composable
private fun BodyText(body: MessageBody) {
    when (body) {
        is MessageBody.Text -> Text(text = body.text, style = MaterialTheme.typography.bodyLarge)
        is MessageBody.Unsupported -> Placeholder(body.kind.labelRes())
        MessageBody.Redacted -> Placeholder(R.string.timeline_redacted)
        MessageBody.Undecryptable -> Placeholder(R.string.timeline_undecryptable)
    }
}

@Composable
private fun Placeholder(@StringRes label: Int) {
    Text(
        text = stringResource(label),
        style = MaterialTheme.typography.bodyMedium,
        fontStyle = FontStyle.Italic,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun Footer(message: TimelineEntry.Message, modifier: Modifier = Modifier) {
    val time = remember(message.timestampMillis) { formatTime(message.timestampMillis) }
    val status = when (message.sendState) {
        SendState.Sent -> null
        SendState.Sending -> R.string.timeline_sending
        SendState.Failed -> R.string.timeline_failed
    }
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (message.isEdited) {
            Text(stringResource(R.string.timeline_edited), style = MaterialTheme.typography.labelSmall)
        }
        status?.let {
            Text(
                text = stringResource(it),
                style = MaterialTheme.typography.labelSmall,
                color = if (message.sendState == SendState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(text = time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@StringRes
private fun UnsupportedKind.labelRes(): Int = when (this) {
    UnsupportedKind.Image -> R.string.timeline_unsupported_image
    UnsupportedKind.Video -> R.string.timeline_unsupported_video
    UnsupportedKind.Audio -> R.string.timeline_unsupported_audio
    UnsupportedKind.File -> R.string.timeline_unsupported_file
    UnsupportedKind.Location -> R.string.timeline_unsupported_location
    UnsupportedKind.Sticker -> R.string.timeline_unsupported_sticker
    UnsupportedKind.Poll -> R.string.timeline_unsupported_poll
    UnsupportedKind.Gallery -> R.string.timeline_unsupported_gallery
    UnsupportedKind.Other -> R.string.timeline_unsupported_other
}

private fun formatTime(millis: Long): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
