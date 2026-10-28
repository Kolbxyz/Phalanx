package org.phalanx.android.ui.screens.catchup

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.phalanx.android.R
import org.phalanx.android.core.ai.AiDisk
import org.phalanx.android.core.ai.CatchUp
import org.phalanx.android.core.ai.LocalSummarizer
import org.phalanx.android.core.ai.SummaryLine
import org.phalanx.android.core.ai.roomsToCatchUp
import org.phalanx.android.data.model.Reminder
import org.phalanx.android.data.model.RoomSummary
import org.phalanx.android.data.repository.ReminderRepository
import org.phalanx.android.data.repository.RoomListRepository
import org.phalanx.android.data.repository.ScheduleRepository
import org.phalanx.android.data.repository.TimelineRepository
import org.phalanx.android.ui.screens.room.ReadAtOpen
import org.phalanx.android.ui.screens.room.aiFailureMessage
import org.phalanx.android.ui.screens.room.summaryOf

/** Where one room's catch-up stands. */
sealed interface CardState {
    /** Not read yet: it comes after the rooms above it, or after "More". */
    data object Waiting : CardState

    data object Reading : CardState

    /** [lastEventId] is the newest message read, which a reminder of a promise opens on. */
    data class Done(val catchUp: CatchUp, val lastEventId: String) : CardState

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
    private val disk: AiDisk,
    private val reminders: ReminderRepository,
    private val schedules: ScheduleRepository,
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
        val readUpTo = suspend { timelines.readUpTo(room.id).getOrNull().orEmpty() }
        val readAtOpen = ReadAtOpen(viewModelScope, readUpTo, room.unreadCount)
        val unread = readAtOpen.unread(session, MAX_MESSAGES)
        val you = context.getString(R.string.summary_you)
        val lines = unread.map {
            SummaryLine(if (it.isOwn) you else it.senderName, summaryOf(it.body, context::getString), it.eventId)
        }
        val last = lines.mapNotNull { it.eventId }.lastOrNull()
        set(room.id, last?.let { catchUp(room.id, lines, it, you) } ?: CardState.Failed(R.string.catch_up_nothing))
    }

    // The same unread messages as last time: the catch-up kept then still holds.
    private suspend fun catchUp(roomId: String, lines: List<SummaryLine>, last: String, you: String): CardState {
        val key = "$roomId $last"
        val kept = disk.read(disk.catchUps, key)
        if (kept != null) return CardState.Done(kept, last)
        return summarizer.catchUp(lines, you).fold(
            onSuccess = {
                disk.keep(disk.catchUps, key, it)
                CardState.Done(it, last)
            },
            onFailure = { CardState.Failed(aiFailureMessage(it)) }
        )
    }

    fun canScheduleExactly(): Boolean = schedules.canScheduleExactly()

    /**
     * Reminds the user at [atMillis] of what they promised in [card]'s room; the reminder opens the
     * room on the newest message read. [onDone] says whether it could be saved.
     */
    fun remindPromise(card: CatchUpCard, atMillis: Long, onDone: (Boolean) -> Unit) {
        val done = card.state as? CardState.Done ?: return onDone(false)
        val promised = done.catchUp.promised ?: return onDone(false)
        val reminder = Reminder(
            id = UUID.randomUUID().toString(),
            roomId = card.room.id,
            eventId = done.lastEventId,
            roomName = card.room.name,
            senderName = context.getString(R.string.catch_up_you_promised),
            preview = promised,
            remindAtMillis = atMillis
        )
        viewModelScope.launch {
            val saved = try {
                reminders.remind(reminder)
                true
            } catch (_: IOException) {
                false
            }
            onDone(saved)
        }
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
