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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.phalanx.android.R
import org.phalanx.android.core.ai.LocalSummarizer
import org.phalanx.android.core.ai.SummaryCheckpoint
import org.phalanx.android.core.ai.SummaryCheckpoints
import org.phalanx.android.core.ai.SummaryLine
import org.phalanx.android.core.ai.SummaryPoint
import org.phalanx.android.core.ai.SummaryScope
import org.phalanx.android.core.ai.speech.Transcriber
import org.phalanx.android.data.model.MessageBody
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
     * [savedAtMillis]: shown again from a checkpoint, written then.
     */
    data class Done(
        val count: Int,
        val points: List<SummaryPoint>,
        val scope: SummaryScope,
        val savedAtMillis: Long? = null
    ) : SummaryState

    data class Failed(@StringRes val message: Int) : SummaryState
}

/**
 * Sums up every message since the last summary's checkpoint, or else since the user last read the
 * room, from their read receipt (see [ReadAtOpen]): those unread when it was opened, and any that
 * came after, up to [MAX_MESSAGES]; with none unread, the last [DEFAULT_MESSAGES]. Each summary
 * leaves a checkpoint after its last message (see [SummaryCheckpoints]); asking again with nothing
 * after it shows that summary again without running the model.
 */
@HiltViewModel
class SummaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val timelines: TimelineRepository,
    roomList: RoomListRepository,
    private val summarizer: LocalSummarizer,
    private val checkpoints: SummaryCheckpoints,
    private val transcriber: Transcriber,
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

    /** This room's checkpoints, oldest first, each shown after the last message its summary covers. */
    val marks: StateFlow<List<SummaryCheckpoint>> =
        checkpoints.of(roomId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    fun start() {
        job?.cancel()
        mutableState.value = SummaryState.Running("")
        job = viewModelScope.launch {
            val session = timelines.open(roomId).getOrNull()
            if (session == null) {
                mutableState.value = SummaryState.Failed(R.string.summary_failed)
                return@launch
            }
            val last = checkpoints.latest(roomId)
            val sinceLast = last?.let { afterCheckpoint(session, it) }
            when {
                last != null && sinceLast?.isEmpty() == true -> showSaved(last)
                sinceLast != null -> summarize(sinceLast.takeLast(MAX_MESSAGES), SummaryScope.SinceLast)
                else -> {
                    val unread = readAtOpen.unread(session, MAX_MESSAGES)
                    if (unread.isNotEmpty()) {
                        summarize(unread, SummaryScope.Unread)
                    } else {
                        summarize(latest(session), SummaryScope.Latest)
                    }
                }
            }
        }
    }

    /** Shows the summary [checkpoint] holds again, as it was written. */
    fun show(checkpoint: SummaryCheckpoint) {
        job?.cancel()
        showSaved(checkpoint)
    }

    private fun showSaved(checkpoint: SummaryCheckpoint) {
        val summary = checkpoint.summary
        mutableState.value =
            SummaryState.Done(summary.messageCount, summary.points, checkpoint.scope, checkpoint.madeAtMillis)
    }

    private suspend fun summarize(messages: List<TimelineEntry.Message>, scope: SummaryScope) {
        val lines = linesOf(messages)
        if (lines.isEmpty()) {
            mutableState.value = SummaryState.Failed(R.string.summary_empty)
            return
        }
        summarizer.summarize(lines) { mutableState.value = SummaryState.Running(it) }.fold(
            onSuccess = { summary ->
                mutableState.value = SummaryState.Done(summary.messageCount, summary.points, scope)
                // A message still sending has no event ID yet; the checkpoint goes after the last that has one.
                lines.lastOrNull { it.eventId != null }?.eventId?.let { eventId ->
                    checkpoints.add(SummaryCheckpoint(roomId, eventId, System.currentTimeMillis(), summary, scope))
                }
            },
            onFailure = { mutableState.value = SummaryState.Failed(aiFailureMessage(it)) }
        )
    }

    // The messages after [checkpoint]'s, or null when it is no longer in the loaded history.
    private fun afterCheckpoint(session: TimelineSession, checkpoint: SummaryCheckpoint): List<TimelineEntry.Message>? {
        val messages = messagesOf(session)
        val at = messages.indexOfFirst { it.eventId == checkpoint.eventId }
        return if (at < 0) null else messages.drop(at + 1)
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

    // A voice message already written out is read as its words, not as "Voice message".
    private suspend fun linesOf(messages: List<TimelineEntry.Message>): List<SummaryLine> = messages.map { message ->
        val sender = if (message.isOwn) context.getString(R.string.summary_you) else message.senderName
        val transcript = (message.body as? MessageBody.Audio)?.let { transcriber.cached(message.eventId ?: message.id) }
        SummaryLine(sender, transcript?.text ?: summaryOf(message.body, context::getString), message.eventId)
    }

    private companion object {
        const val MAX_MESSAGES = 500
        const val DEFAULT_MESSAGES = 100
        const val MAX_PAGES = 4
    }
}
