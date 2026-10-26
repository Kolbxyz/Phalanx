package org.phalanx.android.core.matrix

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.matrix.rustcomponents.sdk.ClientException
import org.matrix.rustcomponents.sdk.Room
import org.matrix.rustcomponents.sdk.RoomMessageLikeEvent
import org.matrix.rustcomponents.sdk.RoomMessageLikeEventsListener
import org.matrix.rustcomponents.sdk.TaskHandle
import org.matrix.rustcomponents.sdk.use
import org.phalanx.android.core.prefs.SettingsStore
import org.phalanx.android.data.model.SessionState
import org.phalanx.android.data.repository.RoomListRepository
import org.phalanx.android.di.ApplicationScope
import uniffi.ruma_events.MessageLikeEventType

/**
 * Feeds [MessageArchive] while "Keep deleted messages" is on: every joined room's messages, as
 * sync delivers them, and the deletions that later point at them. It rides on sync, so it costs
 * nothing while sync is stopped, and adds no request of its own.
 */
@Singleton
internal class MessageArchiver @Inject constructor(
    private val active: ActiveClient,
    private val roomList: RoomListRepository,
    private val settings: SettingsStore,
    private val archive: MessageArchive,
    @ApplicationScope private val scope: CoroutineScope
) {
    private enum class Mode { Keep, Erase, Wait }

    fun start() {
        scope.launch {
            combine(settings.settings.map { it.keepDeletedMessages }, active.state) { keep, state ->
                when {
                    // Still restoring at launch: neither on nor gone, so the copies are left alone.
                    state is SessionState.Restoring -> Mode.Wait
                    keep && state is SessionState.LoggedIn -> Mode.Keep
                    // Off, or signed out: the copies belong to no one who still wants them.
                    else -> Mode.Erase
                }
            }.distinctUntilChanged().collectLatest { mode ->
                when (mode) {
                    Mode.Keep -> keep()
                    Mode.Erase -> archive.clear()
                    Mode.Wait -> Unit
                }
            }
        }
    }

    private suspend fun keep() = coroutineScope {
        archive.load()
        // SDK listeners call back on its threads; one queue keeps a message ahead of its deletion.
        val events = Channel<Captured>(Channel.UNLIMITED)
        launch { events.consumeEach { record(it) } }
        val watched = HashMap<String, RoomWatch>()
        try {
            roomList.rooms.filterNotNull().collect { rooms ->
                val names = rooms.associate { it.id to it.name }
                (watched.keys - names.keys).forEach { watched.remove(it)?.close() }
                names.forEach { (roomId, name) ->
                    val existing = watched[roomId]
                    if (existing != null) {
                        existing.roomName = name
                    } else {
                        active.room(roomId)?.let { watched[roomId] = RoomWatch(it, name, events) }
                    }
                }
            }
        } finally {
            watched.values.forEach { it.close() }
            events.close()
        }
    }

    private suspend fun record(captured: Captured) {
        val event = captured.event
        val change = archiveChange(captured.type, event.contentJson) ?: return
        val now = event.timestamp.toLong()
        when (change) {
            is ArchiveChange.Sent -> archive.update {
                it[event.eventId] = ArchivedMessage(
                    eventId = event.eventId,
                    roomId = captured.roomId,
                    roomName = captured.roomName,
                    senderId = event.sender,
                    kind = change.kind,
                    text = change.text,
                    sentAtMillis = now
                )
            }
            is ArchiveChange.Edited -> archive.update { copies ->
                copies[change.target]?.takeIf { it.senderId == event.sender }?.let {
                    copies[change.target] = it.copy(kind = change.kind, text = change.text, edited = true)
                }
            }
            is ArchiveChange.Deleted -> {
                val senderId = archive.senderOf(change.target) ?: return
                val name = displayName(captured.roomId, senderId)
                archive.update { copies ->
                    copies[change.target]?.let {
                        copies[change.target] = it.copy(senderName = name, deletedAtMillis = now)
                    }
                }
            }
        }
    }

    private suspend fun displayName(roomId: String, userId: String): String? = withContext(Dispatchers.IO) {
        try {
            active.room(roomId)?.use { room -> room.member(userId).displayName?.takeIf { it.isNotBlank() } }
        } catch (_: ClientException) {
            // Left the room, or never loaded: the Matrix ID still says who it was.
            null
        }
    }

    private class Captured(
        val roomId: String,
        val roomName: String,
        val type: ArchivedEventType,
        val event: RoomMessageLikeEvent
    )

    /** One room's subscriptions; owns [room] until [close]. */
    private class RoomWatch(private val room: Room, roomName: String, events: Channel<Captured>) {
        @Volatile
        var roomName: String = roomName

        private val roomId = room.id()
        private val handles: List<TaskHandle> = ArchivedEventType.entries.map { type ->
            room.subscribeToMessageLikeEvents(
                type.sdkType(),
                object : RoomMessageLikeEventsListener {
                    override fun onEvent(event: RoomMessageLikeEvent) {
                        events.trySend(Captured(roomId, this@RoomWatch.roomName, type, event))
                    }
                }
            )
        }

        fun close() {
            handles.forEach {
                it.cancel()
                it.destroy()
            }
            room.destroy()
        }
    }
}

private fun ArchivedEventType.sdkType(): MessageLikeEventType = when (this) {
    ArchivedEventType.Message -> MessageLikeEventType.RoomMessage
    ArchivedEventType.Sticker -> MessageLikeEventType.Sticker
    ArchivedEventType.Redaction -> MessageLikeEventType.RoomRedaction
}
