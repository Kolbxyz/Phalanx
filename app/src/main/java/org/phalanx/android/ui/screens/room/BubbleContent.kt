package org.phalanx.android.ui.screens.room

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.ui.navigation.sharedMedia

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
        BubbleLayout.Media, BubbleLayout.Bare -> Content(message, actions)
        BubbleLayout.Padded -> Column(Modifier.padding(bottom = 10.dp)) {
            message.replyTo?.let {
                ReplyQuote(
                    reply = it,
                    accent = LocalContentColor.current,
                    modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 6.dp)
                )
            }
            Content(message, actions)
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
            MessageText(body, message.senderName, message.isEdited, textPadding)
        }
        is MessageBody.Image -> {
            ImageContent(
                body = body,
                onOpen = { actions.onOpenMedia(message) },
                modifier = Modifier.sharedMedia("media-${message.id}")
            )
            body.caption?.let { Caption(it, message.isEdited, textPadding) }
        }
        is MessageBody.Video -> {
            VideoContent(
                body = body,
                onOpen = { actions.onOpenMedia(message) },
                modifier = Modifier.sharedMedia("media-${message.id}")
            )
            body.caption?.let { Caption(it, message.isEdited, textPadding) }
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
private fun Caption(text: String, edited: Boolean, modifier: Modifier) {
    MessageText(MessageBody.Text(text, null, MessageBody.TextStyle.Normal), senderName = "", edited, modifier)
}

@Composable
private fun BigEmoji(text: String) {
    val style = MaterialTheme.typography.displayMedium
    val scale = LocalTimelinePrefs.current.textScale
    Text(text.trim(), style = style.copy(fontSize = style.fontSize * scale, lineHeight = style.lineHeight * scale))
}
