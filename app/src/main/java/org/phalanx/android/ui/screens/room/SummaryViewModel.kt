package org.phalanx.android.ui.screens.room

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.phalanx.android.R
import org.phalanx.android.core.ai.LocalSummarizer
import org.phalanx.android.core.ai.ModelMissingException
import org.phalanx.android.core.ai.SummariesUnsupportedException
import org.phalanx.android.core.ai.SummaryLine
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.data.repository.RoomListRepository
import org.phalanx.android.data.repository.TimelineRepository
import org.phalanx.android.data.repository.TimelineSession
import org.phalanx.android.ui.navigation.RoomRoute
import javax.inject.Inject

sealed interface SummaryState {
    data object Idle : SummaryState

    /** [count] messages are being read; [text] is the reply so far. */
    data class Running(val count: Int, val text: String) : SummaryState

    data class Done(val count: Int, val text: String) : SummaryState

    data class Failed(@StringRes val message: Int) : SummaryState
}

/**
 * Sums up the messages not read yet when the room was opened, at most [MAX_MESSAGES]; with none
 * unread, the last [MAX_MESSAGES]. The count is taken at open, because opening reads the room.
 */
@HiltViewModel
class SummaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val timelines: TimelineRepository,
    roomList: RoomListRepository,
    private val summarizer: LocalSummarizer,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val roomId = savedStateHandle.toRoute<RoomRoute>().roomId
    private val unreadAtOpen = roomList.rooms.value?.firstOrNull { it.id == roomId }?.unreadCount ?: 0L

    private val mutableState = MutableStateFlow<SummaryState>(SummaryState.Idle)
    val state: StateFlow<SummaryState> = mutableState.asStateFlow()
    private var job: Job? = null

    fun start() {
        job?.cancel()
        mutableState.value = SummaryState.Running(0, "")
        job = viewModelScope.launch {
            val session = timelines.open(roomId).getOrNull()
            if (session == null) {
                mutableState.value = SummaryState.Failed(R.string.summary_failed)
                return@launch
            }
            val window = unreadAtOpen.takeIf { it > 0 }?.coerceAtMost(MAX_MESSAGES.toLong())?.toInt() ?: MAX_MESSAGES
            val lines = linesOf(session, window)
            if (lines.isEmpty()) {
                mutableState.value = SummaryState.Failed(R.string.summary_empty)
                return@launch
            }
            mutableState.value = SummaryState.Running(lines.size, "")
            summarizer.summarize(lines) { mutableState.value = SummaryState.Running(lines.size, it) }.fold(
                onSuccess = { mutableState.value = SummaryState.Done(lines.size, it) },
                onFailure = { mutableState.value = SummaryState.Failed(messageFor(it)) }
            )
        }
    }

    fun dismiss() {
        job?.cancel()
        mutableState.value = SummaryState.Idle
    }

    private fun messageFor(error: Throwable): Int = when (error) {
        is ModelMissingException -> R.string.summary_no_model
        is SummariesUnsupportedException -> R.string.settings_ai_unsupported
        else -> R.string.summary_failed
    }

    // Older pages are loaded until the window is full or the room starts; a few tries cap the wait.
    private suspend fun linesOf(session: TimelineSession, window: Int): List<SummaryLine> {
        var pages = 0
        while (messagesOf(session).size < window && session.hasMoreHistory.value && pages++ < MAX_PAGES) {
            if (session.loadOlder().isFailure) break
        }
        return messagesOf(session).takeLast(window).map { message ->
            val sender = if (message.isOwn) context.getString(R.string.summary_you) else message.senderName
            SummaryLine(sender, summaryOf(message.body, context::getString))
        }
    }

    private fun messagesOf(session: TimelineSession): List<TimelineEntry.Message> =
        session.entries.value.filterIsInstance<TimelineEntry.Message>().filter {
            it.body != MessageBody.Redacted && it.body != MessageBody.Undecryptable
        }

    private companion object {
        const val MAX_MESSAGES = 100
        const val MAX_PAGES = 4
    }
}
