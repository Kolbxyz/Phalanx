package org.phalanx.android.ui.screens.room

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.phalanx.android.R
import org.phalanx.android.data.model.MediaRef
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.data.repository.MediaRepository
import org.phalanx.android.data.repository.TimelineRepository
import org.phalanx.android.data.repository.TimelineSession
import org.phalanx.android.ui.navigation.RoomRoute
import javax.inject.Inject

/** A file ready to hand to another app. */
data class OpenRequest(val uri: Uri, val mimeType: String?)

@HiltViewModel
class RoomViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    timelines: TimelineRepository,
    private val media: MediaRepository
) : ViewModel() {
    val roomId: String = savedStateHandle.toRoute<RoomRoute>().roomId

    private val mutableState = MutableStateFlow<RoomUiState>(RoomUiState.Loading)
    val state: StateFlow<RoomUiState> = mutableState.asStateFlow()

    private val mutableDraft = MutableStateFlow("")
    val draft: StateFlow<String> = mutableDraft.asStateFlow()

    private val mutableReplyingTo = MutableStateFlow<TimelineEntry.Message?>(null)
    val replyingTo: StateFlow<TimelineEntry.Message?> = mutableReplyingTo.asStateFlow()

    /** A one-off message for the snackbar; cleared by [dismissNotice]. */
    private val mutableNotice = MutableStateFlow<Int?>(null)
    val notice: StateFlow<Int?> = mutableNotice.asStateFlow()

    private val mutableOpen = MutableStateFlow<OpenRequest?>(null)
    val openRequest: StateFlow<OpenRequest?> = mutableOpen.asStateFlow()

    private var session: TimelineSession? = null

    init {
        viewModelScope.launch {
            timelines.open(roomId).fold(
                onSuccess = { opened ->
                    session = opened
                    observe(opened)
                },
                onFailure = { mutableState.value = RoomUiState.Unavailable }
            )
        }
    }

    fun onDraftChange(value: String) {
        mutableDraft.value = value
    }

    fun send() {
        val text = mutableDraft.value.trim()
        val current = session
        if (text.isEmpty() || current == null) return
        val replyTo = mutableReplyingTo.value
        viewModelScope.launch {
            // The draft is only cleared once the SDK has accepted the message, so a failure
            // never costs the user what they typed.
            current.send(text, replyTo).fold(
                onSuccess = {
                    mutableDraft.update { if (it.trim() == text) "" else it }
                    mutableReplyingTo.update { if (it == replyTo) null else it }
                },
                onFailure = { notify(R.string.composer_send_failed) }
            )
        }
    }

    fun startReply(message: TimelineEntry.Message) {
        mutableReplyingTo.value = message
    }

    fun cancelReply() {
        mutableReplyingTo.value = null
    }

    fun toggleReaction(message: TimelineEntry.Message, key: String) = act { it.toggleReaction(message, key) }

    fun redact(message: TimelineEntry.Message) = act { it.redact(message) }

    fun openFile(ref: MediaRef, filename: String, mimeType: String?) {
        viewModelScope.launch {
            media.openable(ref, filename, mimeType).fold(
                onSuccess = { mutableOpen.value = OpenRequest(it, mimeType) },
                onFailure = { notify(R.string.timeline_open_failed) }
            )
        }
    }

    fun onOpened() {
        mutableOpen.value = null
    }

    suspend fun audioUri(ref: MediaRef, filename: String, mimeType: String?): Uri? =
        media.openable(ref, filename, mimeType).onFailure { notify(R.string.timeline_open_failed) }.getOrNull()

    fun notify(@StringRes message: Int) {
        mutableNotice.value = message
    }

    fun dismissNotice() {
        mutableNotice.value = null
    }

    fun loadOlder() {
        val current = session ?: return
        // A failed page is transient; the list asks again the next time the user scrolls to the top.
        viewModelScope.launch { current.loadOlder() }
    }

    private fun act(block: suspend (TimelineSession) -> Result<Unit>) {
        val current = session ?: return
        viewModelScope.launch { block(current).onFailure { notify(R.string.timeline_action_failed) } }
    }

    private fun observe(opened: TimelineSession) {
        viewModelScope.launch {
            combine(opened.entries, opened.hasMoreHistory) { entries, more ->
                RoomUiState.Ready(opened.title, opened.isDirect, entries.asReversed(), more)
            }.collect { mutableState.value = it }
        }
        viewModelScope.launch {
            opened.entries
                .map { entries -> entries.lastOrNull { it is TimelineEntry.Message && !it.isOwn }?.id }
                .filterNotNull()
                .distinctUntilChanged()
                .collect { opened.markRead() }
        }
    }
}
