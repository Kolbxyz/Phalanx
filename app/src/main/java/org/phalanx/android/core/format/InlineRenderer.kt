package org.phalanx.android.core.format

import org.phalanx.android.data.model.Member

/**
 * One line of Discord markdown to HTML: emphasis, code, spoilers, links, mention pills and custom
 * emoji. Used for one message only: it collects the [userIds] mentioned and whether anything was [formatted].
 */
internal class InlineRenderer(
    /** Longest name first, so "@Ann Lee" wins over "@Ann". */
    private val mentions: List<Member>,
    private val formatting: Boolean,
    /** The room's custom emoji, shortcode to `mxc://` URL: ":shortcode:" becomes the image. */
    private val emotes: Map<String, String> = emptyMap()
) {
    val userIds = LinkedHashSet<String>()
    var formatted = false
        private set

    fun render(text: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            val next = (if (formatting) formatAt(text, i, out) else -1).takeIf { it > i }
                ?: mentionAt(text, i, out).takeIf { it > i }
                ?: emoteAt(text, i, out)
            if (next > i) {
                formatted = true
                i = next
            } else {
                out.append(escape(text[i].toString()))
                i++
            }
        }
        return out.toString()
    }

    /** Renders the construct starting at [i] into [out]; returns where it ends, or -1 if there is none. */
    private fun formatAt(text: String, i: Int, out: StringBuilder): Int {
        val char = text[i]
        return when {
            char == '\\' && i + 1 < text.length && text[i + 1] in ESCAPABLE -> {
                // Sent as HTML too, so the escaped character arrives without its backslash.
                out.append(escape(text[i + 1].toString()))
                i + 2
            }
            char == '`' -> code(text, i, out)
            char == 'h' -> url(text, i, out)
            char == '[' -> link(text, i, out)
            else -> DELIMITERS.firstNotNullOfOrNull { (delimiter, tags) ->
                if (text.startsWith(delimiter, i)) wrap(text, i, delimiter, tags, out).takeIf { it > 0 } else null
            } ?: -1
        }
    }

    private fun wrap(text: String, i: Int, delimiter: String, tags: Pair<String, String>, out: StringBuilder): Int {
        val underscore = delimiter == "_"
        if (underscore && i > 0 && text[i - 1].isLetterOrDigit()) return -1
        val start = i + delimiter.length
        var close = text.indexOf(delimiter, start)
        while (close > start) {
            val content = text.substring(start, close)
            val end = close + delimiter.length
            // A single "*" or "_" never closes on half of a doubled one.
            val lone = delimiter.length > 1 || (text.getOrNull(end) != delimiter[0] && text[close - 1] != delimiter[0])
            val bounded = !underscore || end == text.length || !text[end].isLetterOrDigit()
            val trimmed = delimiter == "||" || (!content.first().isWhitespace() && !content.last().isWhitespace())
            if (lone && bounded && trimmed && text[close - 1] != '\\') {
                out.append(tags.first).append(render(content)).append(tags.second)
                return end
            }
            close = text.indexOf(delimiter, close + 1)
        }
        return -1
    }

    /** A run of backticks up to the next run as long; the content is never formatted. */
    private fun code(text: String, i: Int, out: StringBuilder): Int {
        var length = 0
        while (text.getOrNull(i + length) == '`') length++
        val fence = "`".repeat(length)
        val close = text.indexOf(fence, i + length)
        if (close <= i + length) return -1
        out.append("<code>").append(escape(text.substring(i + length, close))).append("</code>")
        return close + length
    }

    private fun url(text: String, i: Int, out: StringBuilder): Int {
        if (i > 0 && text[i - 1].isLetterOrDigit()) return -1
        val match = URL.matchAt(text, i) ?: return -1
        val url = match.value.trimEnd { it in TRAILING_PUNCTUATION }
        out.append("<a href=\"${escape(url)}\">${escape(url)}</a>")
        return i + url.length
    }

    /** `[text](https://...)`; other schemes stay literal. */
    private fun link(text: String, i: Int, out: StringBuilder): Int {
        val match = LINK.matchAt(text, i) ?: return -1
        val (label, href) = match.destructured
        out.append("<a href=\"${escape(href)}\">${render(label)}</a>")
        return match.range.last + 1
    }

    /** "@" and a picked member's name, or a full Matrix ID typed out. */
    private fun mentionAt(text: String, i: Int, out: StringBuilder): Int {
        if (text[i] != '@' || (i > 0 && text[i - 1].isLetterOrDigit())) return -1
        mentions.forEach { member ->
            val end = i + 1 + member.name.length
            val named = member.name.isNotEmpty() && text.startsWith(member.name, i + 1)
            if (named && (end == text.length || !text[end].isLetterOrDigit())) {
                pill(member.userId, member.name, out)
                return end
            }
        }
        val userId = USER_ID.matchAt(text, i)?.value?.trimEnd('.') ?: return -1
        pill(userId, userId, out)
        return i + userId.length
    }

    /** ":shortcode:" of an emoji the room offers, as the `<img>` Matrix clients and bridges read. */
    private fun emoteAt(text: String, i: Int, out: StringBuilder): Int {
        val close = if (text[i] == ':' && emotes.isNotEmpty()) text.indexOf(':', i + 1) else -1
        val url = (if (close > i + 1) emotes[text.substring(i + 1, close)] else null) ?: return -1
        val alt = escape(text.substring(i, close + 1))
        out.append("<img data-mx-emoticon src=\"${escape(url)}\" alt=\"$alt\" title=\"$alt\" height=\"$EMOTE_HEIGHT\">")
        return close + 1
    }

    private fun pill(userId: String, label: String, out: StringBuilder) {
        userIds += userId
        out.append("<a href=\"https://matrix.to/#/${escape(userId)}\">${escape(label)}</a>")
    }

    private companion object {
        // Longest first: "***" must not be read as "**" then "*".
        val DELIMITERS = listOf(
            "***" to ("<strong><em>" to "</em></strong>"),
            "**" to ("<strong>" to "</strong>"),
            "__" to ("<u>" to "</u>"),
            "~~" to ("<del>" to "</del>"),
            "||" to ("<span data-mx-spoiler>" to "</span>"),
            "*" to ("<em>" to "</em>"),
            "_" to ("<em>" to "</em>")
        )

        // What Element and Cinny send: the height other clients size the emoji at.
        const val EMOTE_HEIGHT = 32
        const val ESCAPABLE = "\\*_~`|>#-[]()@"
        const val TRAILING_PUNCTUATION = ".,:;!?'\")"
        val URL = Regex("https?://[^\\s<>\"]+")
        val LINK = Regex("\\[([^\\]]+)]\\((https?://[^\\s)]+)\\)")
        val USER_ID = Regex("@[a-z0-9._=/+-]+:[A-Za-z0-9.-]+(?::\\d+)?")
    }
}
