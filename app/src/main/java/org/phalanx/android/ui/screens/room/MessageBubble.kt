package org.phalanx.android.ui.screens.room

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.model.SendState
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.ui.components.MatrixAvatar
import org.phalanx.android.ui.navigation.sharedMedia
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val BubbleRadius = 20.dp
private val TightRadius = 6.dp
private val AvatarSize = 32.dp

/**
 * One message. Consecutive messages from one sender form a run: only the first shows the name,
 * only the last the avatar, and the corners between them tighten so the run reads as one block.
 */
@Composable
internal fun MessageBubble(
    message: TimelineEntry.Message,
    isGroupChat: Boolean,
    startsRun: Boolean,
    endsRun: Boolean,
    actions: MessageActions,
    modifier: Modifier = Modifier
) {
    val own = message.isOwn
    val showAvatar = isGroupChat && !own
    Row(
        modifier = modifier.fillMaxWidth().padding(top = if (startsRun) 6.dp else 0.dp),
        horizontalArrangement = if (own) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (showAvatar) {
            if (endsRun) {
                MatrixAvatar(message.senderName, message.senderAvatar, AvatarSize)
            } else {
                Spacer(Modifier.width(AvatarSize))
            }
            Spacer(Modifier.width(8.dp))
        }
        Column(horizontalAlignment = if (own) Alignment.End else Alignment.Start) {
            if (showAvatar && startsRun) {
                Text(
                    text = message.senderName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 12.dp, bottom = 2.dp)
                )
            }
            SwipeToReply(enabled = message.canReply, onReply = { actions.onReply(message) }) {
                Bubble(message, startsRun, actions)
            }
            if (message.reactions.isNotEmpty()) {
                ReactionRow(
                    reactions = message.reactions,
                    onToggle = { actions.onToggleReaction(message, it) },
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(message: TimelineEntry.Message, startsRun: Boolean, actions: MessageActions) {
    val own = message.isOwn
    val senderTop = if (startsRun) BubbleRadius else TightRadius
    // The corners on the sender's side are tight: always at the bottom, and at the top inside a run.
    val shape = RoundedCornerShape(
        topStart = if (own) BubbleRadius else senderTop,
        topEnd = if (own) senderTop else BubbleRadius,
        bottomEnd = if (own) TightRadius else BubbleRadius,
        bottomStart = if (own) BubbleRadius else TightRadius
    )
    val body = message.body
    val sticker = body is MessageBody.Image && body.isSticker
    Surface(
        shape = shape,
        color = when {
            sticker -> Color.Transparent
            own -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = if (own) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .widthIn(max = 320.dp)
            .clip(shape)
            .combinedClickable(onClick = {}, onLongClick = { actions.onLongPress(message) })
    ) {
        Column {
            message.replyTo?.let { ReplyQuote(it, Modifier.padding(start = 6.dp, end = 6.dp, top = 6.dp)) }
            Content(message, actions)
            Footer(message, Modifier.align(Alignment.End).padding(start = 12.dp, end = 12.dp, bottom = 6.dp))
        }
    }
}

@Composable
private fun Content(message: TimelineEntry.Message, actions: MessageActions) {
    val textPadding = Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp)
    when (val body = message.body) {
        is MessageBody.Text -> MessageText(body, message.senderName, textPadding)
        is MessageBody.Image -> {
            ImageContent(
                body = body,
                onOpen = { actions.onOpenImage(message) },
                modifier = Modifier.sharedMedia("media-${message.id}")
            )
            body.caption?.let { Text(it, style = MaterialTheme.typography.bodyLarge, modifier = textPadding) }
        }
        is MessageBody.Video -> {
            VideoContent(body, onOpen = { actions.onOpenFile(body.media, body.filename, body.mimeType) })
            body.caption?.let { Text(it, style = MaterialTheme.typography.bodyLarge, modifier = textPadding) }
        }
        is MessageBody.Audio -> AudioContent(body, actions.resolveAudio)
        is MessageBody.File -> FileContent(body, actions.onOpenFile)
        is MessageBody.Location -> LocationContent(body)
        MessageBody.Redacted, MessageBody.Undecryptable, is MessageBody.Unsupported -> Text(
            text = stringResource(kindLabel(body)),
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = textPadding
        )
    }
}

@Composable
private fun Footer(message: TimelineEntry.Message, modifier: Modifier = Modifier) {
    val time = remember(message.timestampMillis) { formatTime(message.timestampMillis) }
    val status = when (message.sendState) {
        SendState.Sent -> null
        SendState.Sending -> R.string.timeline_sending
        SendState.Failed -> R.string.timeline_failed
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier = modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val style = MaterialTheme.typography.labelSmall
        if (message.isEdited) Text(stringResource(R.string.timeline_edited), style = style, color = muted)
        status?.let {
            val failed = message.sendState == SendState.Failed
            Text(stringResource(it), style = style, color = if (failed) MaterialTheme.colorScheme.error else muted)
        }
        Text(text = time, style = MaterialTheme.typography.labelSmall, color = muted)
    }
}

private fun formatTime(millis: Long): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
