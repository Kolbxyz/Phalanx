package org.phalanx.android.core.matrix

import java.net.URLDecoder
import org.phalanx.android.data.model.TextMarks

/**
 * Turns what the HTML renderer cannot show into [TextMarks] annotations: mention pills, code (it
 * knows neither `<pre>` nor `<code>`, and collapses a block's whitespace) and spoilers, which
 * Discord bridges send a lot of.
 */
internal object HtmlMarks {
    private val preformatted = Regex(
        "<pre\\b[^>]*>(.*?)</pre\\s*>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val codeTag = Regex("</?code\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val code = Regex(
        "<code\\b[^>]*>(.*?)</code\\s*>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val userPill = Regex(
        "<a\\b[^>]*\\bhref\\s*=\\s*\"https://matrix\\.to/#/((?:@|%40)[^\"?/]+)[^\"]*\"[^>]*>(.*?)</a\\s*>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val spoilerOpen = Regex("<span\\b[^>]*\\bdata-mx-spoiler\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val spanTag = Regex("<(/?)span\\b[^>]*>", RegexOption.IGNORE_CASE)

    fun mark(html: String): String = html
        .replace(preformatted) { match ->
            val lines = match.groupValues[1].replace(codeTag, "").trim('\n')
            val kept = lines.replace("\n", "<br>").replace(" ", "&nbsp;")
            "<div><tt><annotation ${TextMarks.CODE}=\"block\">$kept</annotation></tt></div>"
        }
        .replace(code) { "<tt><annotation ${TextMarks.CODE}=\"inline\">${it.groupValues[1]}</annotation></tt>" }
        .replace(userPill) { match ->
            val userId = URLDecoder.decode(match.groupValues[1], "UTF-8").replace("\"", "&quot;")
            val label = match.groupValues[2].let { if (it.startsWith("@")) it else "@$it" }
            "<annotation ${TextMarks.MENTION}=\"$userId\">$label</annotation>"
        }
        .let(::markSpoilers)

    /** Spoiler spans may hold other spans, so each is closed at the `</span>` that matches it. */
    private fun markSpoilers(html: String): String {
        val out = StringBuilder()
        var from = 0
        while (true) {
            val open = spoilerOpen.find(html, from) ?: break
            var depth = 1
            val close = spanTag.findAll(html, open.range.last + 1).firstOrNull { tag ->
                depth += if (tag.groupValues[1].isEmpty()) 1 else -1
                depth == 0
            } ?: break
            out.append(html, from, open.range.first)
                .append("<annotation ${TextMarks.SPOILER}=\"1\">")
                .append(html, open.range.last + 1, close.range.first)
                .append("</annotation>")
            from = close.range.last + 1
        }
        return out.append(html, from, html.length).toString()
    }
}
