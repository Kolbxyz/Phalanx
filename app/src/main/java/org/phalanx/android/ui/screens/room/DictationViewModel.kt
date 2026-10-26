package org.phalanx.android.ui.screens.room

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.phalanx.android.core.ai.speech.SpeechModelStore
import org.phalanx.android.core.ai.speech.Transcriber
import org.phalanx.android.core.ai.speech.WhisperNative
import org.phalanx.android.core.media.AttachmentPreparer
import org.phalanx.android.data.model.OutgoingMedia

/** Turns a recording into text in the composer instead of sending it as a voice message. */
@HiltViewModel
class DictationViewModel @Inject constructor(
    private val transcriber: Transcriber,
    private val preparer: AttachmentPreparer,
    models: SpeechModelStore
) : ViewModel() {
    /** Whether a speech model is there to dictate with. */
    val available: StateFlow<Boolean> = models.installed
        .map { it != null && WhisperNative.isAvailable }
        .stateIn(viewModelScope, SharingStarted.Eagerly, models.installed.value != null && WhisperNative.isAvailable)

    /**
     * Writes [voice] out, calling [show] with the text so far as it grows, then deletes the
     * recording: it is never sent.
     */
    fun dictate(voice: OutgoingMedia.Voice, show: (String) -> Unit, onFailed: (Int) -> Unit) {
        viewModelScope.launch {
            try {
                // Its own key: a dictation is never shown again, so it must not come from the cache.
                transcriber.transcribe("dictation-${UUID.randomUUID()}", Uri.fromFile(voice.file), show).fold(
                    onSuccess = { show(it.text) },
                    onFailure = { onFailed(transcriptFailure(it)) }
                )
            } finally {
                preparer.discard(voice)
            }
        }
    }
}
