package org.phalanx.android.core.format

import org.phalanx.android.data.model.Member

/**
 * Discord-flavoured markdown to Matrix HTML (`org.matrix.custom.html`). Discord's dialect rather
 * than CommonMark, because `__x__` underlines and `||x||` hides there: mautrix-discord turns `<u>`
 * and `data-mx-spoiler` back into those, so formatting survives the bridge in both directions.
 */
internal object Markdown {
    /** [html] is null when the text has nothing to format; [userIds] are the users it mentions. */
    data class Rendered(val html: String?, val userIds: List<String>)

    /**
     * [mentions] are members picked in the composer: "@" and their name in [text] become a pill.
     * [emotes] are the room's custom emoji by shortcode: ":shortcode:" becomes the image.
     * Without [formatting], only mentions and emoji are rendered and markdown stays literal.
     */
    fun render(
        text: String,
        mentions: List<Member> = emptyList(),
        formatting: Boolean = true,
        emotes: Map<String, String> = emptyMap()
    ): Rendered {
        val inline = InlineRenderer(mentions.sortedByDescending { it.name.length }, formatting, emotes)
        val html = StringBuilder()
        var lineBefore = false
        // Anything other than a plain line (a code block, a list, ...) is formatting by itself.
        var blocks = false
        fun line(content: String) {
            if (lineBefore) html.append("<br>")
            html.append(inline.render(content))
            lineBefore = true
        }
        fun block(content: String) {
            html.append(content)
            lineBefore = false
            blocks = true
        }

        val lines = text.lines()
        var i = 0
        while (i < lines.size) {
            val current = lines[i]
            if (!formatting) {
                line(current)
                i++
                continue
            }
            val fenceEnd = if (current.startsWith(FENCE)) fenceEnd(lines, i) else -1
            when {
                fenceEnd > i -> {
                    val language = current.removePrefix(FENCE).trim().takeIf { it.matches(LANGUAGE) }
                    val code = lines.subList(i + 1, fenceEnd).joinToString("\n")
                    val attribute = language?.let { " class=\"language-$it\"" }.orEmpty()
                    block("<pre><code$attribute>${escape(code)}</code></pre>")
                    i = fenceEnd + 1
                }
                // Discord's ">>> " quotes the rest of the message.
                current.startsWith(QUOTE_REST) -> {
                    val quoted = listOf(current.removePrefix(QUOTE_REST)) + lines.drop(i + 1)
                    block("<blockquote>${quoted.joinToString("<br>") { inline.render(it) }}</blockquote>")
                    i = lines.size
                }
                isQuote(current) -> {
                    val end = run(lines, i, ::isQuote)
                    val quoted = lines.subList(i, end).map { it.removePrefix(">").removePrefix(" ") }
                    block("<blockquote>${quoted.joinToString("<br>") { inline.render(it) }}</blockquote>")
                    i = end
                }
                HEADING.matches(current) -> {
                    val (hashes, title) = HEADING.matchEntire(current)!!.destructured
                    block("<h${hashes.length}>${inline.render(title)}</h${hashes.length}>")
                    i++
                }
                BULLET.matches(current) || NUMBERED.matches(current) -> {
                    val pattern = if (BULLET.matches(current)) BULLET else NUMBERED
                    val tag = if (pattern == BULLET) "ul" else "ol"
                    val end = run(lines, i) { pattern.matches(it) }
                    val items = lines.subList(i, end).joinToString("") { item ->
                        "<li>${inline.render(pattern.matchEntire(item)!!.groupValues[1])}</li>"
                    }
                    block("<$tag>$items</$tag>")
                    i = end
                }
                else -> {
                    line(current)
                    i++
                }
            }
        }
        val formatted = blocks || inline.formatted
        return Rendered(html.toString().takeIf { formatted }, inline.userIds.toList())
    }

    /** Whether [text] mentions the whole room, as "@room" does in every Matrix client. */
    fun mentionsRoom(text: String): Boolean = ROOM_MENTION.containsMatchIn(text)

    private fun isQuote(line: String) = line == ">" || line.startsWith("> ")

    /** The index of the closing fence of the block opened at [start], or -1 when it never closes. */
    private fun fenceEnd(lines: List<String>, start: Int): Int =
        (start + 1 until lines.size).firstOrNull { lines[it].trim() == FENCE } ?: -1

    private inline fun run(lines: List<String>, start: Int, matches: (String) -> Boolean): Int {
        var end = start
        while (end < lines.size && matches(lines[end])) end++
        return end
    }

    private const val FENCE = "```"
    private const val QUOTE_REST = ">>> "
    private val LANGUAGE = Regex("[A-Za-z0-9+#-]+")
    private val HEADING = Regex("(#{1,3}) (.+)")
    private val BULLET = Regex("[-*] (.+)")
    private val NUMBERED = Regex("\\d{1,3}\\. (.+)")
    private val ROOM_MENTION = Regex("(^|\\s)@room\\b")
}

/** Escapes text for HTML content and double-quoted attribute values. */
internal fun escape(text: String): String = buildString(text.length) {
    text.forEach { char ->
        when (char) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            else -> append(char)
        }
    }
}
