package org.phalanx.android.core.matrix

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.matrix.rustcomponents.sdk.ClientException
import org.matrix.rustcomponents.sdk.ReceiptType
import org.matrix.rustcomponents.sdk.Room
import org.matrix.rustcomponents.sdk.use
import org.phalanx.android.core.prefs.SettingsStore
import org.phalanx.android.data.repository.RoomActionsRepository
import org.phalanx.android.data.repository.TimelineUnavailableException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class RustRoomActionsRepository @Inject constructor(
    private val active: ActiveClient,
    private val settings: SettingsStore
) : RoomActionsRepository {
    override suspend fun setFavourite(roomId: String, favourite: Boolean) =
        withRoom(roomId) { it.setIsFavourite(favourite, tagOrder = null) }

    override suspend fun setUnread(roomId: String, unread: Boolean) = withRoom(roomId) { room ->
        if (unread) {
            room.setUnreadFlag(true)
        } else {
            // Private unless the user chose public receipts, as when reading in the room itself.
            val type = if (settings.current().publicReadReceipts) ReceiptType.READ else ReceiptType.READ_PRIVATE
            room.markAsRead(type)
            room.setUnreadFlag(false)
        }
    }

    override suspend fun leave(roomId: String) = withRoom(roomId) { it.leave() }

    private suspend fun withRoom(roomId: String, block: suspend (Room) -> Unit): Result<Unit> =
        withContext(Dispatchers.IO) {
            val room = active.room(roomId) ?: return@withContext Result.failure(TimelineUnavailableException())
            try {
                room.use { block(it) }
                Result.success(Unit)
            } catch (e: ClientException) {
                Result.failure(e)
            }
        }
}
