package org.phalanx.android.ui.screens.catchup

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.phalanx.android.R
import org.phalanx.android.core.ai.CatchUp
import org.phalanx.android.core.ai.LocalSummarizer
import org.phalanx.android.core.ai.SummaryLine
import org.phalanx.android.core.ai.roomsToCatchUp
import org.phalanx.android.data.model.RoomSummary
import org.phalanx.android.data.repository.RoomListRepository
import org.phalanx.android.data.repository.TimelineRepository
import org.phalanx.android.ui.screens.room.ReadAtOpen
import org.phalanx.android.ui.screens.room.aiFailureMessage
import org.phalanx.android.ui.screens.room.summaryOf

/** Where one room's catch-up stands. */
sealed interface CardState {
    /** Not read yet: it comes after the rooms above it, or after "More". */
    data object Waiting : CardState

    data object Reading : CardState

    data class Done(val catchUp: CatchUp) : CardState

    data class Failed(@StringRes val message: Int) : CardState
}

data class CatchUpCard(val room: RoomSummary, val state: CardState)

/**
 * Every room with unread messages, most worth reading first, each caught up on in turn by the
 * local model: the first card fills in within seconds and the rest follow. Only [BATCH] rooms are
 * read at first, as the model is slow on a busy room; "More" reads the next ones. Nothing is
 * marked as read.
 */
@HiltViewModel
class CatchUpViewModel @Inject constructor(
    roomList: RoomListRepository,
    private val timelines: TimelineRepository,
    private val summarizer: LocalSummarizer,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val mutableCards = MutableStateFlow(
        roomsToCatchUp(roomList.rooms.value.orEmpty(), System.currentTimeMillis())
            .map { CatchUpCard(it, CardState.Waiting) }
    )
    val cards: StateFlow<List<CatchUpCard>> = mutableCards.asStateFlow()

    // One room at a time: the model runs one request at a time anyway, and in this order.
    private val reading = Mutex()

    init {
        more()
    }

    /** Reads the next [BATCH] rooms still waiting. */
    fun more() {
        val next = mutableCards.value.filter { it.state == CardState.Waiting }.take(BATCH).map { it.room }
        next.forEach { set(it.id, CardState.Reading) }
        viewModelScope.launch { reading.withLock { next.forEach { read(it) } } }
    }

    private suspend fun read(room: RoomSummary) {
        val session = timelines.open(room.id).getOrNull()
        if (session == null) {
            set(room.id, CardState.Failed(R.string.catch_up_failed))
            return
        }
        val unread = ReadAtOpen(viewModelScope, timelines, room.id, room.unreadCount).unread(session, MAX_MESSAGES)
        val you = context.getString(R.string.summary_you)
        val lines = unread.map {
            SummaryLine(if (it.isOwn) you else it.senderName, summaryOf(it.body, context::getString), it.eventId)
        }
        if (lines.isEmpty()) {
            set(room.id, CardState.Failed(R.string.catch_up_nothing))
            return
        }
        summarizer.catchUp(lines, you).fold(
            onSuccess = { set(room.id, CardState.Done(it)) },
            onFailure = { set(room.id, CardState.Failed(aiFailureMessage(it))) }
        )
    }

    private fun set(roomId: String, state: CardState) = mutableCards.update { cards ->
        cards.map { if (it.room.id == roomId) it.copy(state = state) else it }
    }

    private companion object {
        const val BATCH = 5

        // The newest unread ones: past this, the start of a long backlog matters less than its end.
        const val MAX_MESSAGES = 60
    }
}
