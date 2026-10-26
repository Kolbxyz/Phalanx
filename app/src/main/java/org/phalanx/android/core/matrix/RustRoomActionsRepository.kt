package org.phalanx.android.core.matrix

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.matrix.rustcomponents.sdk.ClientException
import org.matrix.rustcomponents.sdk.CreateRoomParameters
import org.matrix.rustcomponents.sdk.NotificationSettingsException
import org.matrix.rustcomponents.sdk.ReceiptType
import org.matrix.rustcomponents.sdk.Room
import org.matrix.rustcomponents.sdk.RoomNotificationMode
import org.matrix.rustcomponents.sdk.RoomPreset
import org.matrix.rustcomponents.sdk.RoomVisibility
import org.matrix.rustcomponents.sdk.use
import org.phalanx.android.core.prefs.SettingsStore
import org.phalanx.android.data.repository.RoomActionsRepository
import org.phalanx.android.data.repository.TimelineUnavailableException

@Singleton
internal class RustRoomActionsRepository @Inject constructor(
    private val active: ActiveClient,
    private val settings: SettingsStore,
    private val mutes: MuteStates
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

    override suspend fun setMuted(roomId: String, muted: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        val client = active.client() ?: return@withContext Result.failure(TimelineUnavailableException())
        // Shown at once; put back if the server refuses.
        mutes.set(roomId, muted)
        try {
            client.getNotificationSettings().use { settings ->
                if (muted) {
                    settings.setRoomNotificationMode(roomId, RoomNotificationMode.MUTE)
                } else {
                    settings.restoreDefaultRoomNotificationMode(roomId)
                }
            }
            Result.success(Unit)
        } catch (e: NotificationSettingsException) {
            mutes.forget(roomId)
            Result.failure(e)
        }
    }

    override suspend fun leave(roomId: String) = withRoom(roomId) { it.leave() }

    override suspend fun setArchived(roomId: String, archived: Boolean) =
        withRoom(roomId) { it.setIsLowPriority(archived, tagOrder = null) }

    override suspend fun directRoomWith(userId: String): Result<String> = withContext(Dispatchers.IO) {
        val client = active.client() ?: return@withContext Result.failure(TimelineUnavailableException())
        try {
            val existing = client.getDmRoom(userId)?.use { it.id() }
            Result.success(existing ?: client.createRoom(directRoom(userId)))
        } catch (e: ClientException) {
            Result.failure(e)
        }
    }

    private fun directRoom(userId: String) = CreateRoomParameters(
        name = null,
        isEncrypted = true,
        isDirect = true,
        visibility = RoomVisibility.Private,
        preset = RoomPreset.TRUSTED_PRIVATE_CHAT,
        invite = listOf(userId)
    )

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
