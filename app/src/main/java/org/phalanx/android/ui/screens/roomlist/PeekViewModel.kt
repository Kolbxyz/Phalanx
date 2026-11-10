package org.phalanx.android.ui.screens.roomlist

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.repository.TimelineRepository
import org.phalanx.android.ui.screens.room.messagesOf

/** One message in a peek: who sent it (null for the user's own), and what it says. */
@Immutable
data class PeekLine(val sender: String?, val body: MessageBody)

/**
 * The last few messages of the chat a menu is open on, read from its timeline without marking
 * anything read: looking in from the list is not reading. Opening another clears the last one.
 */
@HiltViewModel
class PeekViewModel @Inject constructor(private val timelines: TimelineRepository) : ViewModel() {
    private val mutableLines = MutableStateFlow<List<PeekLine>>(emptyList())
    val lines: StateFlow<List<PeekLine>> = mutableLines.asStateFlow()
    private var loading: Job? = null

    fun peek(roomId: String?) {
        loading?.cancel()
        mutableLines.value = emptyList()
        if (roomId == null) return
        loading = viewModelScope.launch {
            val session = timelines.open(roomId).getOrNull() ?: return@launch
            // A timeline opened just now may still be filling: wait a moment for its first messages.
            withTimeoutOrNull(WAIT_MILLIS) { session.entries.first { entries -> entries.isNotEmpty() } }
            mutableLines.value = messagesOf(session).takeLast(LINES).map {
                PeekLine(if (it.isOwn) null else it.senderName, it.body)
            }
        }
    }

    private companion object {
        const val LINES = 3
        const val WAIT_MILLIS = 1_500L
    }
}
