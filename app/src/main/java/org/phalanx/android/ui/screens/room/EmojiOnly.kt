package org.phalanx.android.ui.screens.room

import java.text.BreakIterator

private const val MAX_BIG_EMOJI = 3

/**
 * True for a short message made only of emoji ("👍", "🎉🎉"), which messengers draw large and
 * without a bubble. Counted in what the reader sees: a family or a flag is one emoji.
 */
internal fun isEmojiOnly(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || !trimmed.codePoints().allMatch { isEmojiPart(it) || Character.isWhitespace(it) }) {
        return false
    }
    val graphemes = BreakIterator.getCharacterInstance().apply { setText(trimmed) }
    var count = 0
    var start = graphemes.first()
    var end = graphemes.next()
    while (end != BreakIterator.DONE) {
        if (trimmed.substring(start, end).isNotBlank()) count++
        start = end
        end = graphemes.next()
    }
    return count <= MAX_BIG_EMOJI
}

private val PICTOGRAPHS = 0x1F000..0x1FAFF
private val DINGBATS = 0x2600..0x27BF

// Joiners, variation selectors, keycaps and tag sequences glue emoji together.
private val VARIATION_SELECTORS = 0xFE00..0xFE0F
private val TAGS = 0xE0020..0xE007F
private const val ZERO_WIDTH_JOINER = 0x200D
private const val KEYCAP = 0x20E3

private fun isEmojiPart(codePoint: Int): Boolean =
    Character.getType(codePoint) == Character.OTHER_SYMBOL.toInt() ||
        codePoint in PICTOGRAPHS ||
        codePoint in DINGBATS ||
        codePoint in VARIATION_SELECTORS ||
        codePoint in TAGS ||
        codePoint == ZERO_WIDTH_JOINER ||
        codePoint == KEYCAP
