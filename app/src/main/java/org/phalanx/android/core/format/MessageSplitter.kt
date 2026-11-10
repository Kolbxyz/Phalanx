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
    fun split(text: String, limit: Int): List<String> = splitWithCuts(text, limit).map { it.text }

    /** As [split], each part saying where it was cut from the next, so the cuts can be shown first. */
    fun splitWithCuts(text: String, limit: Int): List<Part> {
        require(limit > (MAX_FENCE_LINE + CLOSING.length) * 2) { "Limit too small to split into" }
        val parts = mutableListOf<Part>()
        var rest = text
        while (rest.length > limit) {
            val (end, next, reason) = cut(rest, limit - CLOSING.length)
            val part = rest.substring(0, end).trimEnd()
            val opener = openFence(part)
            if (part.isNotEmpty()) parts += Part(if (opener != null) part + CLOSING else part, reason)
            rest = rest.substring(next).let { if (opener != null) opener + "\n" + it else it }
        }
        if (rest.isNotBlank()) parts += Part(rest, cut = null)
        return parts
    }

    /** Where the part ends, where the next starts (the separator between them left out), and why there. */
    private fun cut(text: String, limit: Int): Triple<Int, Int, Cut> {
        val floor = limit / 2
        for ((separator, reason) in SEPARATORS) {
            val at = text.lastIndexOf(separator, limit - separator.length)
            if (at >= floor) return Triple(at, at + separator.length, reason)
        }
        // No break in sight: cut through, but never between the two halves of an emoji.
        val at = if (text[limit - 1].isHighSurrogate()) limit - 1 else limit
        return Triple(at, at, Cut.MidWord)
    }

    /** One message of a split, and how it was cut from the next one; [cut] is null for the last. */
    data class Part(val text: String, val cut: Cut?)

    /** Where a cut fell, best first. */
    enum class Cut { Paragraph, Line, Word, MidWord }

    private val SEPARATORS = listOf("\n\n" to Cut.Paragraph, "\n" to Cut.Line, " " to Cut.Word)

    /** The fence line of a code block [part] leaves open, to start the next part with. */
    private fun openFence(part: String): String? {
        val fences = part.lines().filter { it.startsWith(FENCE) }
        val open = fences.lastOrNull()?.takeIf { fences.size % 2 == 1 } ?: return null
        return open.takeIf { it.length <= MAX_FENCE_LINE } ?: FENCE
    }
}
