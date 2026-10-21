package org.phalanx.android.ui.screens.room

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
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.data.repository.TimelineRepository
import org.phalanx.android.data.repository.TimelineSession
import org.phalanx.android.ui.navigation.RoomRoute
import javax.inject.Inject

@HiltViewModel
class RoomViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    timelines: TimelineRepository
) : ViewModel() {
    private val mutableState = MutableStateFlow<RoomUiState>(RoomUiState.Loading)
    val state: StateFlow<RoomUiState> = mutableState.asStateFlow()

    private val mutableDraft = MutableStateFlow("")
    val draft: StateFlow<String> = mutableDraft.asStateFlow()

    private val mutableSendFailed = MutableStateFlow(false)
    val sendFailed: StateFlow<Boolean> = mutableSendFailed.asStateFlow()

    private var session: TimelineSession? = null

    init {
        val roomId = savedStateHandle.toRoute<RoomRoute>().roomId
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
        viewModelScope.launch {
            // The draft is only cleared once the SDK has accepted the message, so a failure
            // never costs the user what they typed.
            current.send(text).fold(
                onSuccess = { mutableDraft.update { if (it.trim() == text) "" else it } },
                onFailure = { mutableSendFailed.value = true }
            )
        }
    }

    fun dismissSendFailed() {
        mutableSendFailed.value = false
    }

    fun loadOlder() {
        val current = session ?: return
        // A failed page is transient; the list asks again the next time the user scrolls to the top.
        viewModelScope.launch { current.loadOlder() }
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
