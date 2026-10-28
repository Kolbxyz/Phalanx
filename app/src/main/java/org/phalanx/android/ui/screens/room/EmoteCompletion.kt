package org.phalanx.android.ui.screens.room

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.phalanx.android.data.model.CustomEmote

/**
 * Completes the ":name" being typed with the room's custom emoji, as Discord does. Two characters
 * are needed first, so a colon in plain text ("at 10:30", "note:") doesn't open anything.
 */
internal object EmoteCompletion {
    private const val MAX_SUGGESTIONS = 8
    private val beforeCursor = Regex("(?:^|\\s):([A-Za-z0-9_+-]{2,})$")

    private class Word(val start: Int, val end: Int, val query: String)

    private fun wordAt(input: TextFieldValue): Word? {
        val cursor = input.selection.start
        val before = input.text.substring(0, cursor).takeIf { input.selection.collapsed }
        val match = before?.let(beforeCursor::find) ?: return null
        val query = match.groupValues[1]
        // A closing colon typed already goes with the word.
        val end = if (input.text.getOrNull(cursor) == ':') cursor + 1 else cursor
        return Word(start = cursor - query.length - 1, end = end, query = query)
    }

    /** The ":name" being typed at the cursor, without its colon; null when there is none. */
    fun query(input: TextFieldValue): String? = wordAt(input)?.query

    /** [emotes] whose shortcode contains it, in the order given, those that start with it first. */
    fun suggestions(input: TextFieldValue, emotes: List<CustomEmote>): List<CustomEmote> {
        val query = wordAt(input)?.query?.lowercase() ?: return emptyList()
        return emotes
            .filter { it.shortcode.lowercase().contains(query) }
            .sortedBy { if (it.shortcode.lowercase().startsWith(query)) 0 else 1 }
            .take(MAX_SUGGESTIONS)
    }

    /** [input] with the ":name" at the cursor replaced by [pick]'s ":shortcode:", then a space. */
    fun complete(input: TextFieldValue, pick: CustomEmote): TextFieldValue {
        val word = wordAt(input) ?: return input
        val rest = input.text.substring(word.end)
        // A space already following the word is reused, so the text doesn't gain a double one.
        val inserted = ":${pick.shortcode}:" + if (rest.startsWith(" ")) "" else " "
        val text = input.text.substring(0, word.start) + inserted + rest
        return TextFieldValue(text, TextRange(word.start + ":${pick.shortcode}: ".length))
    }
}
