package org.phalanx.android.ui.screens.room

import androidx.annotation.StringRes
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.phalanx.android.R
import org.phalanx.android.core.ai.speech.SpeechModelMissingException
import org.phalanx.android.core.ai.speech.SpeechModelStore
import org.phalanx.android.core.ai.speech.SpeechUnsupportedException
import org.phalanx.android.core.ai.speech.Transcriber
import org.phalanx.android.core.ai.speech.Transcript
import org.phalanx.android.core.ai.speech.UndecodableAudioException
import org.phalanx.android.core.ai.speech.WhisperNative
import org.phalanx.android.core.prefs.SettingsStore
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.repository.MediaRepository

/** Where one voice message's transcript stands. */
sealed interface TranscriptState {
    /** [text] is what is written so far. */
    data class Writing(val text: String) : TranscriptState

    data class Done(val transcript: Transcript) : TranscriptState

    data class Failed(@StringRes val message: Int) : TranscriptState
}

/** The room's voice messages written out as text, by message, as the user asks for each. */
@HiltViewModel
class TranscriptViewModel @Inject constructor(
    private val transcriber: Transcriber,
    private val media: MediaRepository,
    models: SpeechModelStore,
    settings: SettingsStore
) : ViewModel() {
    /** Whether a speech model is there to offer transcripts at all. */
    val available: StateFlow<Boolean> = models.installed
        .map { it != null && WhisperNative.isAvailable }
        .stateIn(viewModelScope, SharingStarted.Eagerly, models.installed.value != null && WhisperNative.isAvailable)

    /** Whether voice messages are written out as they come into view, without asking. */
    val auto: StateFlow<Boolean> = combine(available, settings.settings) { ready, current ->
        ready && current.autoTranscribe
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val mutableStates = MutableStateFlow<Map<String, TranscriptState>>(emptyMap())
    val states: StateFlow<Map<String, TranscriptState>> = mutableStates.asStateFlow()

    /** Writes out [body], known as [key]; a transcript already written shows again at once. */
    fun transcribe(key: String, body: MessageBody.Audio) {
        if (mutableStates.value[key] is TranscriptState.Writing) return
        set(key, TranscriptState.Writing(""))
        viewModelScope.launch {
            val audio = media.openable(body.media, body.filename, body.mimeType).getOrElse {
                set(key, TranscriptState.Failed(R.string.transcript_download_failed))
                return@launch
            }
            transcriber.transcribe(key, audio) { set(key, TranscriptState.Writing(it)) }.fold(
                onSuccess = { set(key, TranscriptState.Done(it)) },
                onFailure = { set(key, TranscriptState.Failed(transcriptFailure(it))) }
            )
        }
    }

    /** Writes out [body] as it comes into view, unless it was already, or is being, written. */
    fun transcribeOnSight(key: String, body: MessageBody.Audio) {
        if (key in mutableStates.value) return
        viewModelScope.launch {
            // A kept transcript shows without fetching the audio again.
            val kept = transcriber.cached(key)
            if (kept != null) set(key, TranscriptState.Done(kept)) else transcribe(key, body)
        }
    }

    /** Shows a transcript written earlier, from another visit to the room, without asking. */
    fun restore(key: String) {
        if (key in mutableStates.value) return
        viewModelScope.launch { transcriber.cached(key)?.let { set(key, TranscriptState.Done(it)) } }
    }

    private fun set(key: String, state: TranscriptState) = mutableStates.update { it + (key to state) }
}

/** What to tell the user when a transcript could not be written. */
@StringRes
internal fun transcriptFailure(error: Throwable): Int = when (error) {
    is SpeechModelMissingException -> R.string.transcript_no_model
    is SpeechUnsupportedException -> R.string.transcript_unsupported
    is UndecodableAudioException -> R.string.transcript_undecodable
    else -> R.string.transcript_failed
}

/** The room's transcripts, for the voice messages deep in its timeline; null outside a room. */
val LocalTranscripts = staticCompositionLocalOf<TranscriptViewModel?> { null }
