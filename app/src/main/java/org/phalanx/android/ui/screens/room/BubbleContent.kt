package org.phalanx.android.ui.screens.room

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.ui.navigation.sharedMedia

private const val MUTED_ALPHA = 0.72f

// A picture inside a padded bubble sits inset, rounded, with the quote and caption wrapping to its width.
private val MediaInset = 4.dp
private val InsetMediaShape = RoundedCornerShape(18.dp)

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
internal fun BubbleContent(
    message: TimelineEntry.Message,
    layout: BubbleLayout,
    actions: MessageActions,
    onLongPress: () -> Unit
) {
    when (layout) {
        BubbleLayout.Media -> Content(message, layout, actions, onLongPress)
        BubbleLayout.Bare -> Box(Modifier.landingPop(message)) { Content(message, layout, actions, onLongPress) }
        BubbleLayout.Padded -> Column(
            Modifier
                .then(if (hasPicture(message.body)) Modifier.width(MediaWidth + MediaInset * 2) else Modifier)
                .padding(bottom = 10.dp)
        ) {
            message.replyTo?.let {
                ReplyQuote(
                    reply = it,
                    // On this account's bubble the theme's accent is the bubble itself; elsewhere it stands out.
                    accent = if (message.isOwn) LocalContentColor.current else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 6.dp),
                    onClick = it.eventId?.let { id -> { actions.onJumpTo(id) } }
                )
            }
            Content(message, layout, actions, onLongPress)
        }
    }
}

/** Media take taps themselves, so they hand a long press on to the bubble's [onLongPress]. */
@Composable
private fun Content(
    message: TimelineEntry.Message,
    layout: BubbleLayout,
    actions: MessageActions,
    onLongPress: () -> Unit
) {
    val textPadding = Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp)
    when (val body = message.body) {
        is MessageBody.Text -> if (isEmojiOnly(body.text) && message.replyTo == null) {
            BigEmoji(body.text)
        } else {
            TranslatableText(
                key = message.eventId ?: message.id,
                body = body,
                original = {
                    MessageText(
                        body,
                        message.senderName,
                        message.isEdited,
                        actions.onOpenProfile,
                        onLongPress,
                        textPadding
                    )
                },
                onLongPress = onLongPress,
                modifier = textPadding
            )
            LinkPreviewCard(body, Modifier.padding(start = MediaInset, end = MediaInset, top = 8.dp))
        }
        is MessageBody.Image -> {
            ImageContent(
                body = body,
                onOpen = { actions.onOpenMedia(message) },
                onLongPress = onLongPress,
                modifier = Modifier.inset(layout).sharedMedia("media-${message.id}")
            )
            body.caption?.let { Caption(it, message.isEdited, actions.onOpenProfile, onLongPress, textPadding) }
        }
        is MessageBody.Video -> {
            VideoContent(
                body = body,
                actions = actions,
                onOpen = { actions.onOpenMedia(message) },
                onLongPress = onLongPress,
                modifier = Modifier.inset(layout).sharedMedia("media-${message.id}")
            )
            body.caption?.let { Caption(it, message.isEdited, actions.onOpenProfile, onLongPress, textPadding) }
        }
        is MessageBody.Audio -> AudioContent(message.id, message.eventId ?: message.id, body, actions.resolveFile)
        is MessageBody.File -> FileContent(body, actions.onOpenFile, onLongPress)
        is MessageBody.Location -> LocationContent(body, onLongPress)
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
private fun Caption(
    caption: MessageBody.Text,
    edited: Boolean,
    onMention: (String) -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier
) {
    MessageText(caption, senderName = "", edited, onMention, onLongPress, modifier)
}

private fun hasPicture(body: MessageBody) = (body is MessageBody.Image && !body.isSticker) || body is MessageBody.Video

private fun Modifier.inset(layout: BubbleLayout) =
    if (layout == BubbleLayout.Padded) padding(MediaInset).clip(InsetMediaShape) else this

@Composable
private fun BigEmoji(text: String) {
    val style = MaterialTheme.typography.displayMedium
    val scale = LocalTimelinePrefs.current.textScale
    Text(text.trim(), style = style.copy(fontSize = style.fontSize * scale, lineHeight = style.lineHeight * scale))
}
