package org.phalanx.android.ui.screens.room

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import org.phalanx.android.core.contacts.PersonTimelineSession
import org.phalanx.android.data.model.RoomSummary
import org.phalanx.android.data.model.ScheduledMessage
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.data.repository.PushRepository
import org.phalanx.android.data.repository.ScheduleRepository

/** A person's thread as the screen shows it: their chats, where a new message goes, and network changes. */
@Immutable
data class PersonThread(
    /** One per network, in the thread's order. */
    val chats: List<RoomSummary>,
    val target: RoomSummary,
    /** The chat of each row that starts a run of messages from one network, by row ID; the tag goes there. */
    val runStarts: Map<String, RoomSummary>
)

/** [session]'s thread, updated as messages arrive and the target changes. */
internal fun personThreadOf(session: PersonTimelineSession, rooms: Flow<List<RoomSummary>?>): Flow<PersonThread?> =
    combine(session.entries, session.target, rooms.filterNotNull()) { entries, target, all ->
        val byId = all.associateBy { it.id }
        byId[target]?.let { PersonThread(session.roomIds.mapNotNull(byId::get), it, runStarts(entries, session, byId)) }
    }

/** The messages waiting to be sent to any of [roomIds], soonest first. */
internal fun scheduledIn(schedules: ScheduleRepository, roomIds: List<String>): Flow<List<ScheduledMessage>> =
    combine(roomIds.map(schedules::scheduled)) { lists -> lists.flatMap { it }.sortedBy { it.sendAtMillis } }

/**
 * Tells push which rooms are on screen, so their messages need no notification: the room at first,
 * every chat of a person's thread once it is open.
 */
internal class ShownRooms(private val push: PushRepository, roomId: String) {
    private var rooms = listOf(roomId)
    private var shown = false

    fun show() {
        shown = true
        rooms.forEach(push::startViewing)
    }

    fun hide() {
        shown = false
        rooms.forEach(push::stopViewing)
    }

    fun update(all: List<String>) {
        if (shown) (all - rooms.toSet()).forEach(push::startViewing)
        rooms = all
    }
}

private fun runStarts(
    entries: List<TimelineEntry>,
    session: PersonTimelineSession,
    rooms: Map<String, RoomSummary>
): Map<String, RoomSummary> {
    val starts = HashMap<String, RoomSummary>()
    var previous: String? = null
    for (entry in entries) {
        val roomId = session.origin(entry.id)?.first ?: continue
        if (roomId != previous) rooms[roomId]?.let { starts[entry.id] = it }
        previous = roomId
    }
    return starts
}
