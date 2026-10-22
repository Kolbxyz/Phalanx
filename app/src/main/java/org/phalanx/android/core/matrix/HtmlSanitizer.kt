package org.phalanx.android.core.matrix

/**
 * Prepares `org.matrix.custom.html` bodies for display. The SDK already sanitizes them; this
 * removes what a phone cannot or should not render, and maps Matrix's own attributes onto ones the
 * HTML renderer understands. Bridges (WhatsApp, Telegram, Signal, ...) send all of these.
 */
internal object HtmlSanitizer {
    private val replyFallback = Regex(
        "<mx-reply>.*?</mx-reply>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val dropped = Regex(
        "<(script|style|iframe|object|embed)\\b.*?</\\1\\s*>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    // Custom emotes are <img> tags pointing at mxc:// media; the alt text (":shortcode:") stands in.
    private val image = Regex("<img\\b[^>]*?>", RegexOption.IGNORE_CASE)
    private val srcAttribute = Regex("\\bsrc\\s*=\\s*(\"(mxc://[^\"]*)\"|'(mxc://[^']*)')", RegexOption.IGNORE_CASE)
    private val altAttribute = Regex("\\balt\\s*=\\s*(\"([^\"]*)\"|'([^']*)')", RegexOption.IGNORE_CASE)
    private val mxColor = Regex("\\bdata-mx-color\\s*=\\s*\"(#[0-9a-fA-F]{6})\"", RegexOption.IGNORE_CASE)
    private val fontColor = Regex(
        "<font\\b([^>]*)\\bcolor\\s*=\\s*\"(#[0-9a-fA-F]{6})\"([^>]*)>",
        RegexOption.IGNORE_CASE
    )

    /** Custom emotes become their alt text. */
    fun sanitize(html: String): String = clean(html, emotes = null)

    /**
     * Custom emotes become `<annotation emote="N">alt</annotation>`, N indexing [SanitizedHtml.emoteUrls],
     * so the text can draw the image in their place and still read the alt text out.
     */
    fun sanitizeWithEmotes(html: String): SanitizedHtml {
        val emotes = ArrayList<String>()
        return SanitizedHtml(clean(html, emotes), emotes)
    }

    private fun clean(html: String, emotes: MutableList<String>?): String = html
        .replace(replyFallback, "")
        .replace(dropped, "")
        .replace(image) { match ->
            val alt = altAttribute.find(match.value)?.let { it.groupValues[2].ifEmpty { it.groupValues[3] } }
            val src = srcAttribute.find(match.value)?.let { it.groupValues[2].ifEmpty { it.groupValues[3] } }
            val text = alt?.let(::escape).orEmpty()
            if (emotes == null || src == null || text.isEmpty()) return@replace text
            emotes += src
            "<annotation emote=\"${emotes.size - 1}\">$text</annotation>"
        }
        .replace(mxColor) { "style=\"color:${it.groupValues[1]}\"" }
        .replace(fontColor) { match ->
            val (before, color, after) = match.destructured
            "<span${before.trimEnd()} style=\"color:$color\"$after>"
        }
        .replace(Regex("</font>", RegexOption.IGNORE_CASE), "</span>")
        .let(HtmlMarks::mark)
        .trim()

    /**
     * Strips the `> <@user:server> quoted text` lines old clients and some bridges put at the top
     * of a plain-text reply. Only call it for messages that really are replies.
     */
    fun stripPlainReplyFallback(body: String): String {
        if (!body.startsWith("> ")) return body
        val lines = body.lines()
        val firstReal = lines.indexOfFirst { !it.startsWith(">") }
        if (firstReal < 0) return body
        return lines.drop(firstReal).dropWhile { it.isBlank() }.joinToString("\n")
    }

    // Attribute values are already entity-encoded; only stray angle brackets could open a tag.
    private fun escape(text: String) = text.replace("<", "&lt;").replace(">", "&gt;")
}

/** Sanitized HTML and the `mxc://` URLs of the custom emotes it marks, in order. */
internal class SanitizedHtml(val html: String, val emoteUrls: List<String>)
