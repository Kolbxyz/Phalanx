package org.phalanx.android.data.repository

import org.phalanx.android.data.model.MessageVersion

interface EditHistoryRepository {
    /** Every version of [eventId] in [roomId], oldest first; fetched from the server when not stored here. */
    suspend fun versions(roomId: String, eventId: String): Result<List<MessageVersion>>
}
