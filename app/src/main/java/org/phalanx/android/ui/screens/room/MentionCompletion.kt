package org.phalanx.android.ui.screens.room

import androidx.compose.runtime.Immutable
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

/** Completes the "@..." at the end of the draft, as Discord and Element do. */
internal object MentionCompletion {
    private const val MAX_SUGGESTIONS = 5
    private val trailing = Regex("(?:^|\\s)@([^\\s@]*)$")

    /** The "@..." being typed at the end of [draft], without its "@"; null when there is none. */
    fun query(draft: String): String? = trailing.find(draft)?.groupValues?.get(1)

    /** [members] in the order given (a member list's, roles first), those whose name starts with it first. */
    fun suggestions(draft: String, members: List<Member>): List<MentionSuggestion> {
        val query = query(draft)?.lowercase() ?: return emptyList()
        val silent = draft.length == query.length + 1 && "silent".startsWith(query)
        val room = "room".startsWith(query)
        val users = members
            .filter { it.name.lowercase().contains(query) || it.userId.lowercase().contains(query) }
            .sortedBy { if (it.name.lowercase().startsWith(query)) 0 else 1 }
            .take(MAX_SUGGESTIONS - listOf(silent, room).count { it })
            .map { MentionSuggestion.User(it) }
        return listOfNotNull(MentionSuggestion.Silent.takeIf { silent }) + users +
            listOfNotNull(MentionSuggestion.Room.takeIf { room })
    }

    /** [draft] with the "@..." being typed replaced by [pick], and a space to carry on. */
    fun complete(draft: String, pick: MentionSuggestion): String {
        val query = query(draft) ?: return draft
        val text = when (pick) {
            is MentionSuggestion.User -> pick.member.name
            MentionSuggestion.Room -> "room"
            MentionSuggestion.Silent -> "silent"
        }
        return draft.dropLast(query.length) + text + " "
    }
}
