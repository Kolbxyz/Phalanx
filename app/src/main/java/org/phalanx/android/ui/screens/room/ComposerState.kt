package org.phalanx.android.ui.screens.room

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.phalanx.android.data.model.CustomEmote
import org.phalanx.android.data.model.Member
import org.phalanx.android.data.model.OutgoingMedia
import org.phalanx.android.data.model.TimelineEntry

/** What the next send does besides posting the text. */
@Immutable
sealed interface ComposerMode {
    data object New : ComposerMode
    data class Reply(val message: TimelineEntry.Message) : ComposerMode
    data class Edit(val message: TimelineEntry.Message) : ComposerMode
}

/** A picked or captured file waiting in the composer. [media] is null while it is still being read. */
@Immutable
data class PendingAttachment(val id: String, val media: OutgoingMedia?)

@Immutable
data class ComposerState(
    /** The field's text, cursor and IME composition; the cursor is where a mention completes. */
    val input: TextFieldValue = TextFieldValue(),
    val mode: ComposerMode = ComposerMode.New,
    val attachments: List<PendingAttachment> = emptyList(),
    /** Loudness samples of the voice message being recorded; null when not recording. */
    val recording: List<Float>? = null,
    /** Members picked from [suggestions]; "@" and their name in the draft is sent as a mention. */
    val mentions: List<Member> = emptyList(),
    val suggestions: List<MentionSuggestion> = emptyList(),
    /** The room's custom emoji matching the ":name" being typed. */
    val emoteSuggestions: List<CustomEmote> = emptyList()
) {
    val draft: String
        get() = input.text

    /** [text] in the field, the cursor at its end. */
    fun withDraft(text: String): ComposerState = copy(input = TextFieldValue(text, TextRange(text.length)))

    val canSend: Boolean
        get() = attachments.any { it.media != null } || draft.isNotBlank()

    /** The send button turns into a microphone when there is nothing else to send. */
    val showsMic: Boolean
        get() = !canSend && attachments.isEmpty() && mode !is ComposerMode.Edit
}
