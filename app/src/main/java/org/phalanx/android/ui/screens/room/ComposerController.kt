package org.phalanx.android.ui.screens.room

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.ui.text.input.TextFieldValue
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.phalanx.android.R
import org.phalanx.android.core.media.AttachmentPreparer
import org.phalanx.android.core.media.ImageEdits
import org.phalanx.android.core.media.VoiceRecorder
import org.phalanx.android.data.model.CustomEmote
import org.phalanx.android.data.model.OutgoingMedia
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.data.repository.ScheduleRepository
import org.phalanx.android.data.repository.TimelineSession
import org.phalanx.android.ui.components.Notice
import org.phalanx.android.ui.components.NoticeKind

/**
 * The room's composer: text, reply or edit target, attachments and voice recording. Owned by
 * [RoomViewModel], which hands it the room's session once open and releases it in [release].
 */
internal class ComposerController(
    private val scope: CoroutineScope,
    private val preparer: AttachmentPreparer,
    private val recorder: VoiceRecorder,
    private val schedules: ScheduleRepository,
    private val notify: (Notice) -> Unit,
    /** Whether the user allows sending typing notices, read at each keystroke. */
    private val shareTyping: () -> Boolean
) {
    private val mutableState = MutableStateFlow(ComposerState())
    val state: StateFlow<ComposerState> = mutableState.asStateFlow()

    private var session: TimelineSession? = null
    private val completions: DraftCompletions = DraftCompletions(scope, { session }) { update(completions::refresh) }
    private val voice =
        VoiceMessageRecorder(scope, recorder, preparer, { session }, { mutableState.value }, ::update, notify)

    init {
        scope.launch {
            recorder.levels.collect { levels ->
                update { if (it.recording != null) it.copy(recording = levels) else it }
            }
        }
    }

    @OptIn(FlowPreview::class)
    fun attach(opened: TimelineSession) {
        session = opened
        scope.launch {
            val saved = opened.loadDraft() ?: return@launch
            update { if (it.draft.isEmpty()) it.withDraft(saved) else it }
        }
        // Saved once typing pauses, so a draft survives the app being killed, not only closed.
        scope.launch {
            state.filter { it.mode !is ComposerMode.Edit }
                .map { it.draft }
                .distinctUntilChanged()
                .drop(1)
                .debounce(DRAFT_SAVE_DELAY_MILLIS)
                .collect { opened.saveDraft(it) }
        }
    }

    fun onDraftChange(typed: TextFieldValue) {
        var value = typed
        update {
            value = typed.asLinkPasteOver(it.input) ?: typed
            completions.refresh(it.copy(input = value))
        }
        session?.setTyping(shareTyping() && value.text.isNotBlank())
    }

    fun pickSuggestion(suggestion: MentionSuggestion) = update { state ->
        val picked = (suggestion as? MentionSuggestion.User)?.member
        state.copy(
            input = MentionCompletion.complete(state.input, suggestion),
            mentions = if (picked != null) state.mentions + picked else state.mentions,
            suggestions = emptyList()
        )
    }

    fun pickEmote(emote: CustomEmote) = update {
        it.copy(input = EmoteCompletion.complete(it.input, emote), emoteSuggestions = emptyList())
    }

    fun clearDraft() = update {
        it.withDraft("").copy(mentions = emptyList(), suggestions = emptyList(), emoteSuggestions = emptyList())
    }

    fun reply(message: TimelineEntry.Message) = update { it.copy(mode = ComposerMode.Reply(message)) }

    /** Puts the message's text in the field; sending then replaces it instead of posting anew. */
    fun edit(message: TimelineEntry.Message, text: String) =
        update { it.withDraft(text).copy(mode = ComposerMode.Edit(message), attachments = emptyList()) }

    /** Leaving edit mode also drops the text being edited, which was never a draft. */
    fun cancelMode() = update {
        when (it.mode) {
            is ComposerMode.Edit -> ComposerState(attachments = it.attachments)
            else -> it.copy(mode = ComposerMode.New)
        }
    }

    /** Content shared from another app: its text replaces the draft, its files join the attachments. */
    fun receive(text: String?, uris: List<Uri>) {
        if (!text.isNullOrBlank()) update { it.withDraft(text) }
        if (uris.isNotEmpty()) addAttachments(uris)
    }

    fun addAttachments(uris: List<Uri>) {
        uris.forEach { uri ->
            val id = UUID.randomUUID().toString()
            update { it.copy(attachments = it.attachments + PendingAttachment(id, null)) }
            scope.launch {
                preparer.prepare(uri).fold(
                    onSuccess = { media ->
                        update { state ->
                            val ready = state.attachments.map { if (it.id == id) it.copy(media = media) else it }
                            state.copy(attachments = ready)
                        }
                    },
                    onFailure = {
                        update { state -> state.copy(attachments = state.attachments.filterNot { it.id == id }) }
                        notify(Notice(R.string.composer_attach_failed, NoticeKind.Error))
                    }
                )
            }
        }
    }

    /** Replaces the picture with its edited copy; the unedited file goes once the copy is ready. */
    fun editAttachment(id: String, edits: ImageEdits) {
        val original = mutableState.value.attachments.firstOrNull {
            it.id == id
        }?.media as? OutgoingMedia.Image ?: return
        if (edits.isUnchanged) return
        scope.launch {
            preparer.edit(original, edits).fold(
                onSuccess = { edited ->
                    update { state ->
                        state.copy(
                            attachments = state.attachments.map {
                                if (it.id == id) it.copy(media = edited) else it
                            }
                        )
                    }
                    preparer.discard(original)
                },
                onFailure = { notify(Notice(R.string.editor_failed, NoticeKind.Error)) }
            )
        }
    }

    fun removeAttachment(id: String) {
        val removed = mutableState.value.attachments.firstOrNull { it.id == id } ?: return
        update { state -> state.copy(attachments = state.attachments.filterNot { it.id == id }) }
        removed.media?.let(preparer::discard)
    }

    fun send() {
        val current = session ?: return
        current.setTyping(false)
        val snapshot = mutableState.value
        if (!snapshot.canSend) return
        val text = snapshot.draft.trim()
        when (val mode = snapshot.mode) {
            is ComposerMode.Edit -> {
                update { it.copy(sending = true) }
                scope.launch {
                    current.edit(mode.message, text, snapshot.mentions).fold(
                        onSuccess = { update { if (it.mode == mode) ComposerState() else it.copy(sending = false) } },
                        onFailure = {
                            update { it.copy(sending = false) }
                            notify(SEND_FAILED)
                        }
                    )
                }
            }
            else -> sendNew(current, snapshot, text, (mode as? ComposerMode.Reply)?.message)
        }
    }

    private fun sendNew(
        current: TimelineSession,
        snapshot: ComposerState,
        text: String,
        replyTo: TimelineEntry.Message?
    ) {
        val media = snapshot.attachments.mapNotNull { it.media }
        if (media.isEmpty()) {
            update { it.copy(sending = true) }
            scope.launch {
                // The text is only cleared once the SDK accepted it, so a failure never loses it.
                current.send(text, snapshot.mentions, replyTo).fold(
                    onSuccess = {
                        update {
                            if (it.draft.trim() != text) return@update it.copy(sending = false)
                            it.withDraft("").copy(mode = ComposerMode.New, mentions = emptyList(), sending = false)
                        }
                    },
                    onFailure = {
                        update { it.copy(sending = false) }
                        notify(SEND_FAILED)
                    }
                )
            }
            return
        }
        // Media goes into the SDK's send queue and shows in the timeline at once, so the composer
        // empties right away. One file takes the text as its caption; several get it as a message first.
        update { state -> ComposerState(attachments = state.attachments.filter { it.media == null }) }
        val caption = text.takeIf { it.isNotEmpty() && media.size == 1 }
        scope.launch {
            if (caption == null && text.isNotEmpty()) {
                current.send(text, snapshot.mentions, replyTo).onFailure { notify(SEND_FAILED) }
            }
            media.forEachIndexed { index, item ->
                launch {
                    current.sendMedia(item, caption, replyTo.takeIf { index == 0 && caption != null })
                        .onFailure { notify(SEND_FAILED) }
                }
            }
        }
    }

    /** Keeps the typed text for [atMillis] instead of sending it; the composer empties. */
    fun schedule(roomId: String, atMillis: Long) {
        val snapshot = mutableState.value
        val text = snapshot.draft.trim()
        if (text.isEmpty() || snapshot.attachments.isNotEmpty() || snapshot.mode is ComposerMode.Edit) return
        val replyTo = (snapshot.mode as? ComposerMode.Reply)?.message?.eventId
        session?.setTyping(false)
        update { it.withDraft("").copy(mode = ComposerMode.New, mentions = emptyList()) }
        scope.launch {
            schedules.schedule(roomId, text, replyTo, atMillis)
            notify(Notice(R.string.schedule_done, NoticeKind.Scheduled, atMillis))
        }
    }

    fun startRecording() = voice.start()

    fun cancelRecording() = voice.cancel()

    fun sendRecording() = voice.send()

    /** Ends the recording and hands its audio over; null when it was too short, which the user is told. */
    fun stopRecording(): OutgoingMedia.Voice? = voice.stop()

    /** Shows [text], dictated so far, after [before]: the draft as it was when the dictation began. */
    fun dictated(before: String, text: String) =
        update { it.withDraft(listOf(before.trimEnd(), text).filter(String::isNotEmpty).joinToString(" ")) }

    fun dictationFailed(@StringRes message: Int) = notify(Notice(message, NoticeKind.Error))

    /** Keeps the draft and drops everything that was only in memory. */
    fun release() {
        session?.setTyping(false)
        recorder.cancel()
        mutableState.value.attachments.mapNotNull { it.media }.forEach(preparer::discard)
        val snapshot = mutableState.value
        if (snapshot.mode !is ComposerMode.Edit) session?.saveDraft(snapshot.draft)
    }

    private fun update(transform: (ComposerState) -> ComposerState) = mutableState.update(transform)

    private companion object {
        const val DRAFT_SAVE_DELAY_MILLIS = 600L
    }
}

private val SEND_FAILED = Notice(R.string.composer_send_failed, NoticeKind.Error)
