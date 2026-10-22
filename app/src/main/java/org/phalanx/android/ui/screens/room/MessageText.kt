package org.phalanx.android.ui.screens.room

import android.util.Patterns
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import org.phalanx.android.R
import org.phalanx.android.data.model.MessageBody

/** Rich text when the sender sent HTML (most clients and bridges do), linkified plain text otherwise. */
@Composable
internal fun MessageText(
    body: MessageBody.Text,
    senderName: String,
    edited: Boolean,
    onMention: (userId: String) -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val links = TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline))
    // "(edited)" trails the last line, small and faint, instead of taking a line of its own.
    val editedLabel = stringResource(R.string.timeline_edited)
    val editedStyle = SpanStyle(
        fontSize = MaterialTheme.typography.labelSmall.fontSize,
        color = LocalContentColor.current.copy(alpha = EDITED_ALPHA)
    )
    val contentColor = LocalContentColor.current
    val background = LocalBubbleColor.current.takeOrElse { MaterialTheme.colorScheme.surface }
    var revealed by remember(body) { mutableStateOf(false) }
    val text = remember(body, senderName, edited, editedStyle, contentColor, background, revealed, onMention) {
        val content = body.html
            ?.let { AnnotatedString.fromHtml(it, links).trimEnd().withInlineEmotes(body.emotes) }
            ?.withMarks(contentColor, background, revealed, onMention) { revealed = true }
            ?: linkify(body.text, links)
        buildAnnotatedString {
            if (body.style == MessageBody.TextStyle.Emote) append("* $senderName ")
            append(content)
            if (edited) withStyle(editedStyle) { append("  ($editedLabel)") }
        }
    }
    val notice = body.style == MessageBody.TextStyle.Notice
    val emotes = remember(body.emotes) { emoteContent(body.emotes) }
    Text(
        text = text,
        modifier = modifier.longPressOnLinks(onLongPress),
        inlineContent = emotes,
        style = messageTextStyle(),
        // Notices are what bots and bridges send about themselves; quieter than people's messages.
        color = if (notice) LocalContentColor.current.copy(alpha = NOTICE_ALPHA) else LocalContentColor.current,
        fontStyle = if (body.style == MessageBody.TextStyle.Emote) FontStyle.Italic else null
    )
}

/** Body text at the size chosen in settings. */
@Composable
internal fun messageTextStyle(): TextStyle {
    val style = MaterialTheme.typography.bodyLarge
    val scale = LocalTimelinePrefs.current.textScale
    return style.copy(fontSize = style.fontSize * scale, lineHeight = style.lineHeight * scale)
}

private const val NOTICE_ALPHA = 0.72f
private const val EDITED_ALPHA = 0.6f

private fun linkify(text: String, styles: TextLinkStyles): AnnotatedString = buildAnnotatedString {
    append(text)
    Patterns.WEB_URL.toRegex().findAll(text).forEach { match ->
        val url = match.value
        // Bare domains ("example.org") are matched too; they still need a scheme to open.
        val target = if (url.contains("://")) url else "https://$url"
        if (target.startsWith("https://") || target.startsWith("http://")) {
            addLink(LinkAnnotation.Url(target, styles), match.range.first, match.range.last + 1)
        }
    }
}

/**
 * A link takes the press it lands on, so the bubble never sees it held. This watches for presses a
 * link took and, once one is held, hands it to [onLongPress] and keeps the link from opening too.
 */
private fun Modifier.longPressOnLinks(onLongPress: () -> Unit) = pointerInput(onLongPress) {
    awaitEachGesture {
        // Links sit inside the text, so by now one has consumed the press if it took it.
        val down = awaitFirstDown(requireUnconsumed = false)
        if (!down.isConsumed) return@awaitEachGesture
        // Null once the timeout passes: the finger stayed down and in place.
        val released = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                val moved = (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                if (!change.pressed || moved) break
            }
        }
        if (released != null) return@awaitEachGesture
        onLongPress()
        // Consumed ahead of the link, so lifting the finger doesn't open it.
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
    }
}

/** HTML paragraphs end in line breaks that would leave a blank line at the bottom of a bubble. */
private fun AnnotatedString.trimEnd(): AnnotatedString {
    val end = text.indexOfLast { !it.isWhitespace() } + 1
    return if (end == text.length) this else subSequence(0, end)
}
