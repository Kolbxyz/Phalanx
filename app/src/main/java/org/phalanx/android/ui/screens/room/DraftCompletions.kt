package org.phalanx.android.ui.screens.room

import kotlinx.coroutines.CoroutineScope
import org.phalanx.android.data.repository.TimelineSession

/**
 * What the word at the cursor completes to: members for "@...", custom emoji for ":name". Each is
 * loaded from the session the first time it is needed; [onLoaded] runs then, to work them out again.
 */
internal class DraftCompletions(
    scope: CoroutineScope,
    private val session: () -> TimelineSession?,
    onLoaded: () -> Unit
) {
    private val mentions = MentionSource(scope, onLoaded)
    private val emotes = EmoteSource(scope, onLoaded)

    /** [state] with the suggestions for its input worked out again. */
    fun refresh(state: ComposerState): ComposerState = state.copy(
        suggestions = mentions.suggestions(state.input, session()),
        emoteSuggestions = emotes.suggestions(state.input, session())
    )
}
