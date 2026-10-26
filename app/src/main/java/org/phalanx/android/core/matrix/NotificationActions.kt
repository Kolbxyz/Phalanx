package org.phalanx.android.core.matrix

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.matrix.rustcomponents.sdk.ClientException
import org.matrix.rustcomponents.sdk.ReceiptType
import org.matrix.rustcomponents.sdk.use
import org.phalanx.android.core.prefs.SettingsStore
import org.phalanx.android.data.model.ScheduledMessage
import org.phalanx.android.data.repository.TimelineUnavailableException

/** What a notification's buttons do to a room, from the background and without opening the app. */
@Singleton
internal class NotificationActions @Inject constructor(
    private val active: ActiveClient,
    private val settings: SettingsStore,
    private val sender: ScheduledSender
) {
    suspend fun markRead(roomId: String): Result<Unit> = withContext(Dispatchers.IO) {
        active.restore()
        val room = active.room(roomId) ?: return@withContext Result.failure(TimelineUnavailableException())
        try {
            // Private unless the user chose public receipts, as when reading in the room itself.
            val type = if (settings.current().publicReadReceipts) ReceiptType.READ else ReceiptType.READ_PRIVATE
            room.use {
                it.markAsRead(type)
                it.setUnreadFlag(false)
            }
            Result.success(Unit)
        } catch (e: ClientException) {
            Result.failure(e)
        }
    }

    /** Sent like a scheduled message that is due now: formatted as the composer does, no sync needed. */
    suspend fun reply(roomId: String, text: String): Result<Unit> =
        sender.send(ScheduledMessage(id = "", roomId = roomId, text = text, sendAtMillis = System.currentTimeMillis()))
}
