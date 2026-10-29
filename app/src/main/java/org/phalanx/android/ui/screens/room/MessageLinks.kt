package org.phalanx.android.ui.screens.room

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import org.phalanx.android.R
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.ui.components.SegmentGap
import org.phalanx.android.ui.components.segmentedShape
import org.phalanx.android.ui.theme.Symbols

private const val MAX_LINKS = 3
private val href = Regex("<a\\b[^>]*\\bhref\\s*=\\s*[\"'](https?://[^\"']+)[\"']", RegexOption.IGNORE_CASE)
private val url = Regex("https?://[^\\s<>\"]+")
private const val TRAILING_PUNCTUATION = ".,:;!?'\")"

/**
 * The web links of a message or caption, once each and in order, at most [MAX_LINKS]: its HTML's
 * anchors (which plain bodies get too, from their markdown), then the URLs typed in its text.
 */
internal fun linksOf(body: MessageBody): List<String> {
    val text = when (body) {
        is MessageBody.Text -> body
        is MessageBody.Image -> body.caption
        is MessageBody.Video -> body.caption
        is MessageBody.File -> body.caption
        else -> null
    } ?: return emptyList()
    val anchors = text.html?.let { html -> href.findAll(html).map { unescape(it.groupValues[1]) } }.orEmpty()
    // HTML only anchors what the sender formatted; the plain body still carries every typed URL.
    val typed = url.findAll(text.text).map { it.value.trimEnd { char -> char in TRAILING_PUNCTUATION } }
    return (anchors + typed).distinct().take(MAX_LINKS).toList()
}

// Attribute values arrive entity-encoded; these are the ones a URL can carry.
private fun unescape(value: String) = value
    .replace("&quot;", "\"")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&#39;", "'")
    .replace("&amp;", "&")

/** One row per link in the long-press sheet, since a link in the text itself only opens on a tap. */
@Composable
internal fun LinkActions(links: List<String>, onCopy: (String) -> Unit, onShare: (String) -> Unit) {
    if (links.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(SegmentGap)) {
        links.forEachIndexed { index, link -> LinkRow(link, segmentedShape(index, links.size), onCopy, onShare) }
    }
}

@Composable
private fun LinkRow(link: String, shape: Shape, onCopy: (String) -> Unit, onShare: (String) -> Unit) {
    ListItem(
        headlineContent = { Text(link, maxLines = 1, overflow = TextOverflow.MiddleEllipsis) },
        leadingContent = { Icon(Symbols.Link, contentDescription = null) },
        trailingContent = {
            Row {
                IconButton(onClick = { onCopy(link) }) {
                    Icon(Symbols.ContentCopy, contentDescription = stringResource(R.string.action_copy_link))
                }
                IconButton(onClick = { onShare(link) }) {
                    Icon(Symbols.Share, contentDescription = stringResource(R.string.action_share_link))
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
    )
}
