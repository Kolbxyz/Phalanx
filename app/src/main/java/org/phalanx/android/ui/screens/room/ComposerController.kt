package org.phalanx.android.ui.screens.room

import android.net.Uri
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
import org.phalanx.android.core.media.VoiceRecorder
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.data.repository.TimelineSession
import java.util.UUID

/**
 * The room's composer: text, reply or edit target, attachments and voice recording. Owned by
 * [RoomViewModel], which hands it the room's session once open and releases it in [release].
 */
internal class ComposerController(
    private val scope: CoroutineScope,
    private val preparer: AttachmentPreparer,
    private val recorder: VoiceRecorder,
    private val notify: (Int) -> Unit,
    /** Whether the user allows sending typing notices, read at each keystroke. */
    private val shareTyping: () -> Boolean
) {
    private val mutableState = MutableStateFlow(ComposerState())
    val state: StateFlow<ComposerState> = mutableState.asStateFlow()

    private var session: TimelineSession? = null

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
            update { if (it.draft.isEmpty()) it.copy(draft = saved) else it }
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

    fun onDraftChange(value: String) {
        update { it.copy(draft = value) }
        session?.setTyping(shareTyping() && value.isNotBlank())
    }

    fun reply(message: TimelineEntry.Message) = update { it.copy(mode = ComposerMode.Reply(message)) }

    /** Puts the message's text in the field; sending then replaces it instead of posting anew. */
    fun edit(message: TimelineEntry.Message, text: String) =
        update { it.copy(mode = ComposerMode.Edit(message), draft = text, attachments = emptyList()) }

    /** Leaving edit mode also drops the text being edited, which was never a draft. */
    fun cancelMode() = update {
        when (it.mode) {
            is ComposerMode.Edit -> ComposerState(attachments = it.attachments)
            else -> it.copy(mode = ComposerMode.New)
        }
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
                        notify(R.string.composer_attach_failed)
                    }
                )
            }
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
            is ComposerMode.Edit -> scope.launch {
                current.edit(mode.message, text).fold(
                    onSuccess = { update { if (it.mode == mode) ComposerState() else it } },
                    onFailure = { notify(R.string.composer_send_failed) }
                )
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
            scope.launch {
                // The text is only cleared once the SDK accepted it, so a failure never loses it.
                current.send(text, replyTo).fold(
                    onSuccess = {
                        update { if (it.draft.trim() == text) it.copy(draft = "", mode = ComposerMode.New) else it }
                    },
                    onFailure = { notify(R.string.composer_send_failed) }
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
                current.send(text, replyTo).onFailure { notify(R.string.composer_send_failed) }
            }
            media.forEachIndexed { index, item ->
                launch {
                    current.sendMedia(item, caption, replyTo.takeIf { index == 0 && caption != null })
                        .onFailure { notify(R.string.composer_send_failed) }
                }
            }
        }
    }

    fun startRecording() {
        recorder.start(scope).fold(
            onSuccess = { update { it.copy(recording = emptyList()) } },
            onFailure = { notify(R.string.composer_record_failed) }
        )
    }

    fun cancelRecording() {
        recorder.cancel()
        update { it.copy(recording = null) }
    }

    fun sendRecording() {
        val voice = recorder.stop()
        val replyTo = (mutableState.value.mode as? ComposerMode.Reply)?.message
        update { it.copy(recording = null) }
        if (voice == null) {
            notify(R.string.composer_record_too_short)
            return
        }
        val current = session ?: return preparer.discard(voice)
        if (replyTo != null) update { it.copy(mode = ComposerMode.New) }
        scope.launch { current.sendMedia(voice, null, replyTo).onFailure { notify(R.string.composer_send_failed) } }
    }

    /** Keeps the draft and drops everything that was only in memory. */
    fun release() {
        session?.setTyping(false)
        recorder.cancel()
        mutableState.value.attachments.mapNotNull { it.media }.forEach(preparer::discard)
        val snapshot = mutableState.value
        if (snapshot.mode !is ComposerMode.Edit) session?.saveDraft(snapshot.draft)
    }

    private inline fun update(transform: (ComposerState) -> ComposerState) = mutableState.update(transform)

    private companion object {
        const val DRAFT_SAVE_DELAY_MILLIS = 600L
    }
}
