package org.phalanx.android.ui.screens.room

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.phalanx.android.R
import org.phalanx.android.core.media.AttachmentPreparer
import org.phalanx.android.core.media.VoiceRecorder
import org.phalanx.android.data.model.OutgoingMedia
import org.phalanx.android.data.repository.TimelineSession
import org.phalanx.android.ui.components.Notice
import org.phalanx.android.ui.components.NoticeKind

/** Records a voice message and sends it as an attachment; owned by [ComposerController]. */
internal class VoiceMessageRecorder(
    private val scope: CoroutineScope,
    private val recorder: VoiceRecorder,
    private val preparer: AttachmentPreparer,
    private val session: () -> TimelineSession?,
    private val state: () -> ComposerState,
    private val update: ((ComposerState) -> ComposerState) -> Unit,
    private val notify: (Notice) -> Unit
) {
    fun start() {
        recorder.start(scope).fold(
            onSuccess = { update { it.copy(recording = emptyList()) } },
            onFailure = { notify(Notice(R.string.composer_record_failed, NoticeKind.Error)) }
        )
    }

    fun cancel() {
        recorder.cancel()
        update { it.copy(recording = null) }
    }

    fun send() {
        val voice = stop() ?: return
        val replyTo = (state().mode as? ComposerMode.Reply)?.message
        val current = session() ?: return preparer.discard(voice)
        if (replyTo != null) update { it.copy(mode = ComposerMode.New) }
        scope.launch { current.sendMedia(voice, null, replyTo).onFailure { notify(SEND_FAILED) } }
    }

    /** Ends the recording and hands its audio over; null when it was too short, which the user is told. */
    fun stop(): OutgoingMedia.Voice? {
        val voice = recorder.stop()
        update { it.copy(recording = null) }
        if (voice == null) notify(Notice(R.string.composer_record_too_short, NoticeKind.Error))
        return voice
    }
}

private val SEND_FAILED = Notice(R.string.composer_send_failed, NoticeKind.Error)
