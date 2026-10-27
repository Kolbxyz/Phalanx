package org.phalanx.android.ui.screens.room

import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.phalanx.android.data.model.CustomEmote
import org.phalanx.android.data.repository.TimelineSession

/**
 * The room's custom emoji to complete ":name" with, loaded the first time one is typed and kept
 * while the room is open. [onLoaded] runs once they are in, so the suggestions can be worked out again.
 */
internal class EmoteSource(private val scope: CoroutineScope, private val onLoaded: () -> Unit) {
    private var emotes: List<CustomEmote>? = null

    fun suggestions(input: TextFieldValue, session: TimelineSession?): List<CustomEmote> {
        if (EmoteCompletion.query(input) == null) return emptyList()
        if (emotes == null && session != null) {
            emotes = emptyList()
            scope.launch {
                emotes = session.emotes()
                onLoaded()
            }
        }
        return EmoteCompletion.suggestions(input, emotes.orEmpty())
    }
}
