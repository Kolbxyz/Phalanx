package org.phalanx.android.ui.screens.room

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
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
import org.phalanx.android.ui.navigation.sharedMedia
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val MUTED_ALPHA = 0.72f

/** How a message sits in its bubble. */
internal enum class BubbleLayout {
    /** Text and everything else: padded content with the time underneath. */
    Padded,

    /** A photo or video on its own: edge to edge, the time floating on it. */
    Media,

    /** A sticker or a few emoji: no bubble at all, drawn large. */
    Bare
}

internal fun bubbleLayout(message: TimelineEntry.Message): BubbleLayout {
    val body = message.body
    return when {
        body is MessageBody.Image && body.isSticker -> BubbleLayout.Bare
        body is MessageBody.Text && body.style == MessageBody.TextStyle.Normal && message.replyTo == null &&
            isEmojiOnly(body.text) -> BubbleLayout.Bare
        message.replyTo != null -> BubbleLayout.Padded
        body is MessageBody.Image && body.caption == null -> BubbleLayout.Media
        body is MessageBody.Video && body.caption == null -> BubbleLayout.Media
        else -> BubbleLayout.Padded
    }
}

@Composable
internal fun BubbleContent(message: TimelineEntry.Message, layout: BubbleLayout, actions: MessageActions) {
    when (layout) {
        BubbleLayout.Media -> Box {
            Content(message, actions)
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.7f),
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
            ) {
                Footer(message, Modifier.padding(horizontal = 8.dp, vertical = 2.dp), muted = false)
            }
        }
        BubbleLayout.Bare -> Column(horizontalAlignment = if (message.isOwn) Alignment.End else Alignment.Start) {
            Content(message, actions)
            Footer(message, Modifier.padding(horizontal = 4.dp))
        }
        BubbleLayout.Padded -> Column {
            message.replyTo?.let {
                ReplyQuote(
                    reply = it,
                    accent = LocalContentColor.current,
                    modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 6.dp)
                )
            }
            Content(message, actions)
            Footer(
                message,
                Modifier.align(Alignment.End).padding(start = 14.dp, end = 14.dp, bottom = 8.dp, top = 2.dp)
            )
        }
    }
}

@Composable
private fun Content(message: TimelineEntry.Message, actions: MessageActions) {
    val textPadding = Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp)
    when (val body = message.body) {
        is MessageBody.Text -> if (isEmojiOnly(body.text) && message.replyTo == null) {
            BigEmoji(body.text)
        } else {
            MessageText(body, message.senderName, textPadding)
        }
        is MessageBody.Image -> {
            ImageContent(
                body = body,
                onOpen = { actions.onOpenImage(message) },
                modifier = Modifier.sharedMedia("media-${message.id}")
            )
            body.caption?.let { Caption(it, textPadding) }
        }
        is MessageBody.Video -> {
            VideoContent(body, onOpen = { actions.onOpenFile(body.media, body.filename, body.mimeType) })
            body.caption?.let { Caption(it, textPadding) }
        }
        is MessageBody.Audio -> AudioContent(body, actions.resolveAudio)
        is MessageBody.File -> FileContent(body, actions.onOpenFile)
        is MessageBody.Location -> LocationContent(body)
        MessageBody.Redacted, MessageBody.Undecryptable, is MessageBody.Unsupported -> Text(
            text = stringResource(kindLabel(body)),
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            color = LocalContentColor.current.copy(alpha = MUTED_ALPHA),
            modifier = textPadding
        )
    }
}

@Composable
private fun Caption(text: String, modifier: Modifier) {
    Text(text, style = messageTextStyle(), modifier = modifier)
}

@Composable
private fun BigEmoji(text: String) {
    val style = MaterialTheme.typography.displayMedium
    val scale = LocalTimelinePrefs.current.textScale
    Text(text.trim(), style = style.copy(fontSize = style.fontSize * scale, lineHeight = style.lineHeight * scale))
}

/** Edited, sending or failed, then the time; quiet unless something went wrong. */
@Composable
private fun Footer(message: TimelineEntry.Message, modifier: Modifier = Modifier, muted: Boolean = true) {
    val time = remember(message.timestampMillis) { formatTime(message.timestampMillis) }
    val tint = if (muted) LocalContentColor.current.copy(alpha = MUTED_ALPHA) else LocalContentColor.current
    val style = MaterialTheme.typography.labelSmall
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (message.isEdited) Text(stringResource(R.string.timeline_edited), style = style, color = tint)
        Text(text = time, style = style, color = tint)
        when (message.sendState) {
            SendState.Sent -> Unit
            SendState.Sending -> Icon(
                Icons.Rounded.Schedule,
                contentDescription = stringResource(R.string.timeline_sending),
                tint = tint,
                modifier = Modifier.size(12.dp)
            )
            SendState.Failed -> Icon(
                Icons.Rounded.ErrorOutline,
                contentDescription = stringResource(R.string.timeline_failed),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

private fun formatTime(millis: Long): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
