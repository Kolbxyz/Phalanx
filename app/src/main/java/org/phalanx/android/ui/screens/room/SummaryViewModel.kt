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

    /** [sinceRead]: the summary covers what was unread at open, and what came after. */
    data class Done(val count: Int, val text: String, val sinceRead: Boolean) : SummaryState

    data class Failed(@StringRes val message: Int) : SummaryState
}

/**
 * Sums up every message since the user last read the room: those unread when it was opened, and
 * any that came after, up to [MAX_MESSAGES]; with none unread, the last [DEFAULT_MESSAGES]. The
 * count is taken at open, because opening reads the room. Asking again with nothing new reuses
 * the last summary (see [LocalSummarizer]).
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

    // The first message unread at open, found on the first summary; later ones start from it too.
    private var firstUnreadId: String? = null

    fun start() {
        job?.cancel()
        mutableState.value = SummaryState.Running(0, "")
        job = viewModelScope.launch {
            val session = timelines.open(roomId).getOrNull()
            if (session == null) {
                mutableState.value = SummaryState.Failed(R.string.summary_failed)
                return@launch
            }
            val sinceRead = unreadAtOpen > 0
            val lines = linesOf(session, sinceRead)
            if (lines.isEmpty()) {
                mutableState.value = SummaryState.Failed(R.string.summary_empty)
                return@launch
            }
            mutableState.value = SummaryState.Running(lines.size, "")
            summarizer.summarize(lines) { mutableState.value = SummaryState.Running(lines.size, it) }.fold(
                onSuccess = { mutableState.value = SummaryState.Done(lines.size, it, sinceRead) },
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

    // Older pages are loaded until the first unread message is there or the room starts; a page cap
    // bounds the wait.
    private suspend fun linesOf(session: TimelineSession, sinceRead: Boolean): List<SummaryLine> {
        val wanted = if (sinceRead) unreadAtOpen.coerceAtMost(MAX_MESSAGES.toLong()).toInt() else DEFAULT_MESSAGES
        var pages = 0
        while (firstUnreadId == null && messagesOf(session).size < wanted && session.hasMoreHistory.value &&
            pages++ < MAX_PAGES
        ) {
            if (session.loadOlder().isFailure) break
        }
        val messages = messagesOf(session)
        val window = if (sinceRead) {
            val anchor = firstUnreadId ?: messages.takeLast(wanted).firstOrNull()?.id?.also { firstUnreadId = it }
            messages.dropWhile { it.id != anchor }.ifEmpty { messages.takeLast(wanted) }.takeLast(MAX_MESSAGES)
        } else {
            messages.takeLast(wanted)
        }
        return window.map { message ->
            val sender = if (message.isOwn) context.getString(R.string.summary_you) else message.senderName
            SummaryLine(sender, summaryOf(message.body, context::getString))
        }
    }

    private fun messagesOf(session: TimelineSession): List<TimelineEntry.Message> =
        session.entries.value.filterIsInstance<TimelineEntry.Message>().filter {
            it.body != MessageBody.Redacted && it.body != MessageBody.Undecryptable
        }

    private companion object {
        const val MAX_MESSAGES = 500
        const val DEFAULT_MESSAGES = 100
        const val MAX_PAGES = 20
    }
}
