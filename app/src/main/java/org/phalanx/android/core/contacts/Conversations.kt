package org.phalanx.android.core.contacts

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.phalanx.android.data.model.Person
import org.phalanx.android.data.repository.PeopleRepository
import org.phalanx.android.data.repository.RoomListRepository
import org.phalanx.android.data.repository.TimelineRepository
import org.phalanx.android.data.repository.TimelineSession
import org.phalanx.android.di.ApplicationScope

/**
 * The conversation a room is part of: for a chat linked to the same person or group on other
 * networks, one thread across all of them; for any other room, its own timeline. Everything on a conversation's
 * screen opens it here, so all of it sees the same rows.
 */
@Singleton
class Conversations @Inject constructor(
    private val timelines: TimelineRepository,
    private val people: PeopleRepository,
    private val roomList: RoomListRepository,
    @ApplicationScope private val scope: CoroutineScope
) {
    private val lock = Mutex()

    // The last few threads, by their rooms; each runs in its own scope, cancelled when it is dropped.
    private val threads = LinkedHashMap<List<String>, Thread>()

    private class Thread(
        val session: PersonTimelineSession,
        val chats: List<TimelineSession>,
        val scope: CoroutineScope
    )

    suspend fun open(roomId: String): Result<TimelineSession> {
        val person = personOf(roomId) ?: return timelines.open(roomId)
        val roomIds = person.chats.map { it.id }.sorted()
        val opened = roomIds.map { timelines.open(it) }
        val failure = opened.firstOrNull { it.isFailure }
        // Any group among them puts sender names back on the messages.
        val isDirect = person.chats.all { it.isDirect || it.dmUserId != null }
        return failure ?: Result.success(thread(roomIds, opened.map { it.getOrThrow() }, person.name, isDirect).session)
    }

    private suspend fun thread(
        roomIds: List<String>,
        chats: List<TimelineSession>,
        name: String,
        isDirect: Boolean
    ) = lock.withLock {
        val kept = threads.remove(roomIds)
        // A chat closed and reopened since (evicted, or another account): its old timeline is dead.
        val thread = kept?.takeIf { it.chats == chats } ?: run {
            kept?.scope?.cancel()
            val threadScope = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext.job))
            Thread(PersonTimelineSession(roomIds.zip(chats).toMap(), name, isDirect, threadScope), chats, threadScope)
        }
        threads[roomIds] = thread
        while (threads.size > MAX_THREADS) threads.remove(threads.keys.first())?.scope?.cancel()
        thread
    }

    /** Where this account's read receipts point in every room of [roomId]'s conversation. */
    suspend fun readUpTo(roomId: String): Set<String> =
        roomsOf(roomId).flatMap { timelines.readUpTo(it).getOrNull().orEmpty() }.toSet()

    /**
     * The room and row ID, in that room's own timeline, of the row [entryId] of [roomId]'s
     * conversation: in a person's thread, the chat the message came from.
     */
    suspend fun originOf(roomId: String, entryId: String): Pair<String, String> =
        (open(roomId).getOrNull() as? PersonTimelineSession)?.origin(entryId) ?: (roomId to entryId)

    /** Where a message sent from [roomId]'s screen goes: the thread's chosen network, else the room. */
    suspend fun targetOf(roomId: String): String =
        (open(roomId).getOrNull() as? PersonTimelineSession)?.target?.value ?: roomId

    /** The rooms of [roomId]'s conversation: the person's chats when linked, else the room alone. */
    suspend fun roomsOf(roomId: String): List<String> =
        personOf(roomId)?.chats?.map { it.id }?.sorted() ?: listOf(roomId)

    /** The person or group [roomId] is one of two linked chats or more of; null when it is not linked. */
    private suspend fun personOf(roomId: String): Person? {
        val links = people.links.filterNotNull().first()
        if (links.groups.isEmpty()) return null
        val rooms = roomList.rooms.filterNotNull().first()
        return peopleOf(rooms, links).firstOrNull { person ->
            person.chats.size > 1 && person.chats.any { it.id == roomId }
        }
    }

    private companion object {
        const val MAX_THREADS = 2
    }
}
