package org.phalanx.android.ui.screens.room

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.phalanx.android.data.model.Member

/** What the "@..." being typed can complete to. */
@Immutable
sealed interface MentionSuggestion {
    data class User(val member: Member) : MentionSuggestion

    /** "@room" notifies everyone in the room. */
    data object Room : MentionSuggestion

    /** "@silent", at the very start, sends without notifying anyone. */
    data object Silent : MentionSuggestion
}

/** Completes the "@..." word under the cursor, wherever it is in the draft, as Discord and Element do. */
internal object MentionCompletion {
    private const val MAX_SUGGESTIONS = 5
    private const val WORD_END = ",.;:!?)"
    private val beforeCursor = Regex("(?:^|\\s)@([^\\s@]*)$")

    /** The "@..." word under the cursor, from its "@" to the end of the word. */
    private class Word(val start: Int, val end: Int, val query: String)

    private fun wordAt(input: TextFieldValue): Word? {
        if (!input.selection.collapsed) return null
        val cursor = input.selection.start
        val match = beforeCursor.find(input.text.substring(0, cursor)) ?: return null
        val query = match.groupValues[1]
        // The rest of the word goes too, up to a space or the punctuation that follows a name.
        val end = input.text.indexOfFirst(cursor) { it.isWhitespace() || it in WORD_END }
        return Word(start = cursor - query.length - 1, end = end, query = query)
    }

    /** The "@..." being typed at the cursor, without its "@"; null when there is none. */
    fun query(input: TextFieldValue): String? = wordAt(input)?.query

    /** [members] in the order given (a member list's, roles first), those whose name starts with it first. */
    fun suggestions(input: TextFieldValue, members: List<Member>): List<MentionSuggestion> {
        val word = wordAt(input) ?: return emptyList()
        val query = word.query.lowercase()
        val silent = word.start == 0 && "silent".startsWith(query)
        val room = "room".startsWith(query)
        val users = members
            .filter { it.name.lowercase().contains(query) || it.userId.lowercase().contains(query) }
            .sortedBy { if (it.name.lowercase().startsWith(query)) 0 else 1 }
            .take(MAX_SUGGESTIONS - listOf(silent, room).count { it })
            .map { MentionSuggestion.User(it) }
        return listOfNotNull(MentionSuggestion.Silent.takeIf { silent }) + users +
            listOfNotNull(MentionSuggestion.Room.takeIf { room })
    }

    /** [input] with the "@..." word at the cursor replaced by [pick], the cursor after it and a space. */
    fun complete(input: TextFieldValue, pick: MentionSuggestion): TextFieldValue {
        val word = wordAt(input) ?: return input
        val name = when (pick) {
            is MentionSuggestion.User -> pick.member.name
            MentionSuggestion.Room -> "room"
            MentionSuggestion.Silent -> "silent"
        }
        val rest = input.text.substring(word.end)
        // A space already following the word is reused, so the text doesn't gain a double one.
        val inserted = "@$name" + if (rest.startsWith(" ")) "" else " "
        val text = input.text.substring(0, word.start) + inserted + rest
        return TextFieldValue(text, TextRange(word.start + "@$name ".length))
    }

    private inline fun String.indexOfFirst(from: Int, predicate: (Char) -> Boolean): Int {
        for (i in from until length) if (predicate(this[i])) return i
        return length
    }
}
