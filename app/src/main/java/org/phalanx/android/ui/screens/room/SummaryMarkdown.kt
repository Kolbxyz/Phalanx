package org.phalanx.android.ui.screens.room

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

// Bold first, so "**" is never read as two italic markers.
private val Inline = Regex("""\*\*(.+?)\*\*|__(.+?)__|\*(.+?)\*|(?<!\w)_(.+?)_(?!\w)|`(.+?)`""")
private val Heading = Regex("""^#{1,6}\s+""")
private val Bullet = Regex("""^\s*[-*+]\s+""")

/**
 * The little Markdown a model writes, as styled text: headings, bullets, bold, italics and code.
 * Anything else stays as written; the markers of a half-streamed span simply show until it closes.
 */
internal fun summaryMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    text.trim().lines().forEachIndexed { index, raw ->
        if (index > 0) append('\n')
        when {
            Heading.containsMatchIn(raw) -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                inline(raw.replace(Heading, ""))
            }
            Bullet.containsMatchIn(raw) -> {
                append("•  ")
                inline(raw.replace(Bullet, ""))
            }
            else -> inline(raw)
        }
    }
}

private fun AnnotatedString.Builder.inline(line: String) {
    var at = 0
    for (match in Inline.findAll(line)) {
        append(line.substring(at, match.range.first))
        val (bold, under, star, italic, code) = match.destructured
        val style = when {
            bold.isNotEmpty() || under.isNotEmpty() -> SpanStyle(fontWeight = FontWeight.Bold)
            code.isNotEmpty() -> SpanStyle(fontFamily = FontFamily.Monospace)
            else -> SpanStyle(fontStyle = FontStyle.Italic)
        }
        withStyle(style) { append(bold + under + star + italic + code) }
        at = match.range.last + 1
    }
    append(line.substring(at))
}
