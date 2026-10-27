package org.phalanx.android.core.matrix

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.matrix.rustcomponents.sdk.ClientException
import org.matrix.rustcomponents.sdk.ReceiptThread
import org.matrix.rustcomponents.sdk.ReceiptType
import org.matrix.rustcomponents.sdk.use
import org.phalanx.android.core.media.AttachmentPreparer
import org.phalanx.android.core.prefs.SettingsStore
import org.phalanx.android.data.repository.TimelineRepository
import org.phalanx.android.data.repository.TimelineSession
import org.phalanx.android.data.repository.TimelineUnavailableException
import org.phalanx.android.di.ApplicationScope

/**
 * Keeps the most recently opened timelines alive, so going back into a room shows it at once
 * instead of rebuilding it from the store. Everything is released when the account changes.
 */
@Singleton
internal class RustTimelineRepository @Inject constructor(
    private val active: ActiveClient,
    private val settings: SettingsStore,
    private val attachments: AttachmentPreparer,
    private val drafts: RustDraftsRepository,
    private val emotes: RoomEmotes,
    @ApplicationScope scope: CoroutineScope
) : TimelineRepository {
    private val lock = Mutex()

    // Access order, so the eldest entry is the least recently opened room.
    private val open = LinkedHashMap<String, RustTimelineSession>(CACHE_SIZE, LOAD_FACTOR, true)

    init {
        // Any change of sync service means sign-in or sign-out: the cached rooms belong to a client
        // that is gone or about to be.
        scope.launch { active.syncService.collect { lock.withLock { closeAll() } } }
    }

    override suspend fun open(roomId: String): Result<TimelineSession> = lock.withLock {
        open[roomId]?.let { return@withLock Result.success(it) }
        withContext(Dispatchers.IO) {
            try {
                val room = active.room(roomId) ?: return@withContext Result.failure(TimelineUnavailableException())
                val session = RustTimelineSession.open(room, settings, attachments, drafts, emotes)
                open[roomId] = session
                evictOverflow()
                Result.success(session)
            } catch (e: ClientException) {
                Result.failure(TimelineUnavailableException(e))
            }
        }
    }

    override suspend fun readUpTo(roomId: String): Result<Set<String>> = withContext(Dispatchers.IO) {
        val room = active.room(roomId) ?: return@withContext Result.failure(TimelineUnavailableException())
        try {
            room.use {
                val me = room.ownUserId()
                val receipts = listOf(ReceiptType.READ, ReceiptType.READ_PRIVATE).flatMap { type ->
                    listOf(ReceiptThread.Unthreaded, ReceiptThread.Main).mapNotNull { thread ->
                        room.loadUserReceipt(type, thread, me)?.eventId
                    }
                }
                Result.success(receipts.toSet())
            }
        } catch (e: ClientException) {
            Result.failure(e)
        }
    }

    override suspend fun forward(fromRoomId: String, eventId: String, toRoomId: String): Result<Unit> {
        val source = open(fromRoomId).getOrElse { return Result.failure(it) } as RustTimelineSession
        val target = open(toRoomId).getOrElse { return Result.failure(it) } as RustTimelineSession
        return source.forward(eventId, target)
    }

    private fun evictOverflow() {
        val iterator = open.values.iterator()
        while (open.size > CACHE_SIZE) {
            iterator.next().close()
            iterator.remove()
        }
    }

    private fun closeAll() {
        open.values.forEach { it.close() }
        open.clear()
    }

    private companion object {
        const val CACHE_SIZE = 5
        const val LOAD_FACTOR = 0.75f
    }
}
