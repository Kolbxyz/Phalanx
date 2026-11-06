package org.phalanx.android.core.format

/**
 * Cuts a message into parts of at most a given length, for a network that refuses longer ones.
 * Cuts fall between paragraphs, else lines, else words, and only a word longer than a whole part
 * is cut through. A code block cut in two is closed and reopened, so both halves stay code.
 */
internal object MessageSplitter {
    private const val FENCE = "```"

    // A longer fence line is reopened bare, so reopening never outgrows a part.
    private const val MAX_FENCE_LINE = 32

    // Room for closing a code block that a cut falls in.
    private const val CLOSING = "\n" + FENCE

    /** [text] in parts of at most [limit] UTF-16 units, as Kotlin counts length; one part if it fits. */
    fun split(text: String, limit: Int): List<String> {
        require(limit > (MAX_FENCE_LINE + CLOSING.length) * 2) { "Limit too small to split into" }
        val parts = mutableListOf<String>()
        var rest = text
        while (rest.length > limit) {
            val (end, next) = cut(rest, limit - CLOSING.length)
            val part = rest.substring(0, end).trimEnd()
            val opener = openFence(part)
            if (part.isNotEmpty()) parts += if (opener != null) part + CLOSING else part
            rest = rest.substring(next).let { if (opener != null) opener + "\n" + it else it }
        }
        if (rest.isNotBlank()) parts += rest
        return parts
    }

    /** Where the part ends and where the next starts, the separator between them left out. */
    private fun cut(text: String, limit: Int): Pair<Int, Int> {
        val floor = limit / 2
        for (separator in listOf("\n\n", "\n", " ")) {
            val at = text.lastIndexOf(separator, limit - separator.length)
            if (at >= floor) return at to at + separator.length
        }
        // No break in sight: cut through, but never between the two halves of an emoji.
        val at = if (text[limit - 1].isHighSurrogate()) limit - 1 else limit
        return at to at
    }

    /** The fence line of a code block [part] leaves open, to start the next part with. */
    private fun openFence(part: String): String? {
        val fences = part.lines().filter { it.startsWith(FENCE) }
        val open = fences.lastOrNull()?.takeIf { fences.size % 2 == 1 } ?: return null
        return open.takeIf { it.length <= MAX_FENCE_LINE } ?: FENCE
    }
}
