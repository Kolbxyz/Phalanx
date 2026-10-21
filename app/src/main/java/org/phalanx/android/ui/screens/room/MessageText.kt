package org.phalanx.android.ui.screens.room

import android.util.Patterns
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextDecoration
import org.phalanx.android.R
import org.phalanx.android.data.model.MessageBody

/** Rich text when the sender sent HTML (most clients and bridges do), linkified plain text otherwise. */
@Composable
internal fun MessageText(
    body: MessageBody.Text,
    senderName: String,
    edited: Boolean,
    modifier: Modifier = Modifier
) {
    val links = TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline))
    // "(edited)" trails the last line, small and faint, instead of taking a line of its own.
    val editedLabel = stringResource(R.string.timeline_edited)
    val editedStyle = SpanStyle(
        fontSize = MaterialTheme.typography.labelSmall.fontSize,
        color = LocalContentColor.current.copy(alpha = EDITED_ALPHA)
    )
    val text = remember(body, senderName, edited, editedStyle) {
        val content = body.html?.let { AnnotatedString.fromHtml(it, links).trimEnd() } ?: linkify(body.text, links)
        buildAnnotatedString {
            if (body.style == MessageBody.TextStyle.Emote) append("* $senderName ")
            append(content)
            if (edited) withStyle(editedStyle) { append("  ($editedLabel)") }
        }
    }
    val notice = body.style == MessageBody.TextStyle.Notice
    Text(
        text = text,
        modifier = modifier,
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

/** HTML paragraphs end in line breaks that would leave a blank line at the bottom of a bubble. */
private fun AnnotatedString.trimEnd(): AnnotatedString {
    val end = text.indexOfLast { !it.isWhitespace() } + 1
    return if (end == text.length) this else subSequence(0, end)
}
