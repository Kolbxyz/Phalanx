package org.phalanx.android.core.matrix

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import org.matrix.rustcomponents.sdk.RoomListEntriesDynamicFilterKind
import org.matrix.rustcomponents.sdk.RoomListEntriesListener
import org.matrix.rustcomponents.sdk.RoomListEntriesUpdate
import org.matrix.rustcomponents.sdk.SyncService
import org.phalanx.android.data.model.RoomSummary
import org.phalanx.android.data.repository.RoomListRepository
import org.phalanx.android.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class RustRoomListRepository @Inject constructor(
    active: ActiveClient,
    @ApplicationScope scope: CoroutineScope
) : RoomListRepository {
    // Kept warm for the whole session, so returning to the list never waits on the SDK again.
    // With sync stopped in the background the subscription is idle and costs nothing.
    @OptIn(ExperimentalCoroutinesApi::class)
    override val rooms: StateFlow<List<RoomSummary>?> = active.syncService
        .flatMapLatest { service -> service?.roomSummaries() ?: flowOf(null) }
        .stateIn(scope, SharingStarted.Eagerly, null)
}

/**
 * The SDK reports the list as a stream of diffs against its own sorted order. Folding them here
 * keeps that order, and each [Room] is released as soon as it has been summarized.
 */
private fun SyncService.roomSummaries(): Flow<List<RoomSummary>> =
    roomUpdates()
        .runningFold(emptyList<RoomSummary>()) { current, updates -> current.applying(updates) }
        // The seed is not a real list yet; the first emission is the SDK's own.
        .drop(1)

private fun SyncService.roomUpdates(): Flow<List<RoomListEntriesUpdate>> = callbackFlow {
    val roomList = roomListService().allRooms()
    val listener = object : RoomListEntriesListener {
        override fun onUpdate(roomEntriesUpdate: List<RoomListEntriesUpdate>) {
            trySendBlocking(roomEntriesUpdate)
        }
    }
    val result = roomList.entriesWithDynamicAdapters(pageSize = Int.MAX_VALUE.toUInt(), listener = listener)
    val controller = result.controller()
    try {
        controller.setFilter(
            RoomListEntriesDynamicFilterKind.All(
                listOf(RoomListEntriesDynamicFilterKind.Joined, RoomListEntriesDynamicFilterKind.NonSpace)
            )
        )
        awaitClose()
    } finally {
        result.entriesStream().cancel()
        controller.destroy()
        result.destroy()
        roomList.destroy()
    }
}.buffer(Channel.UNLIMITED)

private suspend fun List<RoomSummary>.applying(updates: List<RoomListEntriesUpdate>): List<RoomSummary> {
    val list = toMutableList()
    for (update in updates) {
        when (update) {
            is RoomListEntriesUpdate.Append -> list.addAll(update.values.map { it.toSummary() })
            is RoomListEntriesUpdate.PushBack -> list.add(update.value.toSummary())
            is RoomListEntriesUpdate.PushFront -> list.add(0, update.value.toSummary())
            is RoomListEntriesUpdate.Insert -> list.add(update.index.toInt(), update.value.toSummary())
            is RoomListEntriesUpdate.Set -> list[update.index.toInt()] = update.value.toSummary()
            is RoomListEntriesUpdate.Remove -> list.removeAt(update.index.toInt())
            is RoomListEntriesUpdate.Reset -> {
                list.clear()
                list.addAll(update.values.map { it.toSummary() })
            }
            is RoomListEntriesUpdate.Truncate -> list.subList(update.length.toInt(), list.size).clear()
            RoomListEntriesUpdate.PopBack -> list.removeLastOrNull()
            RoomListEntriesUpdate.PopFront -> list.removeFirstOrNull()
            RoomListEntriesUpdate.Clear -> list.clear()
        }
    }
    return list
}
