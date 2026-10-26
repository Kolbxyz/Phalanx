package org.phalanx.android.core.matrix

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.matrix.rustcomponents.sdk.ClientException
import org.matrix.rustcomponents.sdk.Room
import org.matrix.rustcomponents.sdk.TimelineDiff
import org.matrix.rustcomponents.sdk.TimelineListener
import org.phalanx.android.core.prefs.RoomPrefsStore
import org.phalanx.android.core.push.ActivityAlerts
import org.phalanx.android.data.model.RoomPrefs
import org.phalanx.android.data.model.TimelineEntry

/**
 * Typing and read alerts for the rooms the user chose. Runs only while the app syncs: typing notices
 * and read receipts are never pushed, so outside the app there is nothing to watch, and nothing here
 * keeps the app awake to watch for them.
 */
@Singleton
internal class RoomWatch @Inject constructor(
    private val active: ActiveClient,
    private val roomPrefs: RoomPrefsStore,
    private val alerts: ActivityAlerts
) {
    /** Watches until cancelled, following the user's choices as they change. */
    suspend fun run() {
        roomPrefs.prefs
            .map { all -> all.orEmpty().filterValues { it.typingAlerts || it.readAlerts } }
            .distinctUntilChanged()
            .collectLatest { watched ->
                coroutineScope { watched.forEach { (roomId, prefs) -> launch { watch(roomId, prefs) } } }
            }
    }

    private suspend fun watch(roomId: String, prefs: RoomPrefs) {
        val room = try {
            active.room(roomId)
        } catch (_: ClientException) {
            null
        } ?: return
        try {
            val name = room.displayName() ?: roomId
            coroutineScope {
                if (prefs.typingAlerts) launch { watchTyping(room, roomId, name) }
                if (prefs.readAlerts) launch { watchReads(room, roomId, name) }
            }
        } finally {
            room.destroy()
        }
    }

    private suspend fun watchTyping(room: Room, roomId: String, name: String): Unit = coroutineScope {
        val typing = RoomTyping(room, room.ownUserId(), this)
        try {
            var shown = emptySet<String>()
            typing.members.collect { members ->
                val ids = members.map { it.userId }.toSet()
                when {
                    ids.isEmpty() -> alerts.stoppedTyping(roomId)
                    // Only someone starting to type alerts, not the same people still at it.
                    (ids - shown).isNotEmpty() -> alerts.typing(roomId, name, members.map { it.name })
                }
                shown = ids
            }
        } finally {
            typing.close()
            alerts.stoppedTyping(roomId)
        }
    }

    private suspend fun watchReads(room: Room, roomId: String, name: String) {
        val own = room.ownUserId()
        val timeline = try {
            room.timeline()
        } catch (_: ClientException) {
            return
        }
        val items = TimelineItems(MappingContext(own) {})
        val handle = timeline.addListener(
            object : TimelineListener {
                override fun onUpdate(diff: List<TimelineDiff>) = items.apply(diff)
            }
        )
        try {
            var last: Readers? = null
            items.entries.map(::readersOfLatestOwn).distinctUntilChanged().collect { now ->
                val before = last
                last = now
                // The first state seen, or a new message of the user's, sets what is already known.
                if (now == null || before == null || before.eventId != now.eventId) return@collect
                val newReaders = now.userIds - before.userIds
                if (newReaders.isNotEmpty()) alerts.read(roomId, name, now.userIds.map { nameOf(room, it) })
            }
        } finally {
            handle.cancel()
            handle.destroy()
            timeline.destroy()
        }
    }

    private suspend fun nameOf(room: Room, userId: String): String = try {
        room.memberDisplayName(userId)?.takeIf { it.isNotBlank() } ?: userId
    } catch (_: ClientException) {
        userId
    }
}

/** Who has read up to the user's latest message: whose receipt sits on it or on anything after. */
internal data class Readers(val eventId: String, val userIds: Set<String>)

internal fun readersOfLatestOwn(entries: List<TimelineEntry>): Readers? {
    val messages = entries.filterIsInstance<TimelineEntry.Message>()
    val at = messages.indexOfLast { it.isOwn && it.eventId != null }
    if (at < 0) return null
    val readers = messages.subList(at, messages.size).flatMap { it.readBy }.toSet()
    return Readers(checkNotNull(messages[at].eventId), readers)
}
