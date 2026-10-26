package org.phalanx.android.ui.screens.room

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.phalanx.android.R
import org.phalanx.android.core.ai.LocalSummarizer
import org.phalanx.android.core.ai.SummaryLine
import org.phalanx.android.core.ai.SummaryPoint
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.data.repository.RoomListRepository
import org.phalanx.android.data.repository.TimelineRepository
import org.phalanx.android.data.repository.TimelineSession
import org.phalanx.android.ui.navigation.RoomRoute

sealed interface SummaryState {
    data object Idle : SummaryState

    /** [text] is the reply so far. */
    data class Running(val text: String) : SummaryState

    /**
     * [count] messages were read, fewer than asked when the oldest did not fit the model.
     * [sinceRead]: the summary covers what was unread at open, and what came after.
     */
    data class Done(
        val count: Int,
        val points: List<SummaryPoint>,
        val sinceRead: Boolean,
        val replies: Replies = Replies.Offered
    ) : SummaryState

    data class Failed(@StringRes val message: Int) : SummaryState
}

/** Replies the model could suggest once the summary is done. */
sealed interface Replies {
    data object Offered : Replies

    data object Writing : Replies

    data class Ready(val replies: List<String>) : Replies

    data class Failed(@StringRes val message: Int) : Replies
}

/**
 * Sums up every message since the user last read the room, from their read receipt (see
 * [ReadAtOpen]): those unread when it was opened, and any that came after, up to [MAX_MESSAGES];
 * with none unread, the last [DEFAULT_MESSAGES]. Asking again with nothing new reuses the last
 * summary (see [LocalSummarizer]).
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
    private val readAtOpen = ReadAtOpen(
        viewModelScope, timelines, roomId,
        unreadCount = roomList.rooms.value?.firstOrNull { it.id == roomId }?.unreadCount ?: 0L
    )

    private val mutableState = MutableStateFlow<SummaryState>(SummaryState.Idle)
    val state: StateFlow<SummaryState> = mutableState.asStateFlow()
    private var job: Job? = null

    // What the summary was made from, for the reply suggestions that may follow.
    private var summarized: List<SummaryLine> = emptyList()

    fun start() {
        job?.cancel()
        mutableState.value = SummaryState.Running("")
        job = viewModelScope.launch {
            val session = timelines.open(roomId).getOrNull()
            if (session == null) {
                mutableState.value = SummaryState.Failed(R.string.summary_failed)
                return@launch
            }
            val unread = readAtOpen.unread(session, MAX_MESSAGES)
            val sinceRead = unread.isNotEmpty()
            val lines = linesOf(unread.ifEmpty { latest(session) })
            if (lines.isEmpty()) {
                mutableState.value = SummaryState.Failed(R.string.summary_empty)
                return@launch
            }
            summarized = lines
            summarizer.summarize(lines) { mutableState.value = SummaryState.Running(it) }.fold(
                onSuccess = { mutableState.value = SummaryState.Done(it.messageCount, it.points, sinceRead) },
                onFailure = { mutableState.value = SummaryState.Failed(aiFailureMessage(it)) }
            )
        }
    }

    /** Asks the model for three replies to the summed-up messages, shown under the summary. */
    fun suggestReplies() {
        val done = mutableState.value as? SummaryState.Done ?: return
        mutableState.value = done.copy(replies = Replies.Writing)
        job = viewModelScope.launch {
            val replies = summarizer.suggestReplies(summarized, context.getString(R.string.summary_you)).fold(
                onSuccess = { if (it.isEmpty()) Replies.Failed(R.string.summary_failed) else Replies.Ready(it) },
                onFailure = { Replies.Failed(aiFailureMessage(it)) }
            )
            mutableState.value = done.copy(replies = replies)
        }
    }

    fun dismiss() {
        job?.cancel()
        mutableState.value = SummaryState.Idle
    }

    // With nothing unread, the latest messages; older pages are loaded until there are enough or
    // the room starts, and a page cap bounds the wait.
    private suspend fun latest(session: TimelineSession): List<TimelineEntry.Message> {
        var pages = 0
        while (messagesOf(session).size < DEFAULT_MESSAGES && session.hasMoreHistory.value && pages++ < MAX_PAGES) {
            if (session.loadOlder().isFailure) break
        }
        return messagesOf(session).takeLast(DEFAULT_MESSAGES)
    }

    private fun linesOf(messages: List<TimelineEntry.Message>): List<SummaryLine> = messages.map { message ->
        val sender = if (message.isOwn) context.getString(R.string.summary_you) else message.senderName
        SummaryLine(sender, summaryOf(message.body, context::getString), message.eventId)
    }

    private companion object {
        const val MAX_MESSAGES = 500
        const val DEFAULT_MESSAGES = 100
        const val MAX_PAGES = 4
    }
}
