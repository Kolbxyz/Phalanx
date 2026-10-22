package org.phalanx.android.ui.screens.room

import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.phalanx.android.data.model.Member
import org.phalanx.android.data.repository.TimelineSession

/**
 * The room's members to mention, loaded the first time "@" is typed and kept while the room is
 * open. [onLoaded] runs once they are in, so the suggestions shown can be worked out again.
 */
internal class MentionSource(private val scope: CoroutineScope, private val onLoaded: () -> Unit) {
    private var members: List<Member>? = null

    fun suggestions(input: TextFieldValue, session: TimelineSession?): List<MentionSuggestion> {
        if (MentionCompletion.query(input) == null) return emptyList()
        if (members == null && session != null) {
            members = emptyList()
            scope.launch {
                members = session.joinedMembers()
                onLoaded()
            }
        }
        return MentionCompletion.suggestions(input, members.orEmpty())
    }
}
