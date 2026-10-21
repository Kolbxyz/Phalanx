package org.phalanx.android.data.repository

import kotlinx.coroutines.flow.Flow
import org.phalanx.android.data.model.ScheduledMessage

/**
 * Messages kept on this phone and sent at a chosen time, even with the app closed or after a
 * restart. The phone must be on at that time; it sends as soon as it has a network.
 */
interface ScheduleRepository {
    /** [roomId]'s scheduled messages, soonest first. */
    fun scheduled(roomId: String): Flow<List<ScheduledMessage>>

    suspend fun schedule(roomId: String, text: String, replyToEventId: String?, sendAtMillis: Long)

    suspend fun cancel(id: String)

    /** Sends it now instead of at its time. */
    suspend fun sendNow(id: String)

    /** False when the system only allows approximate alarms: messages may go out a few minutes late. */
    fun canScheduleExactly(): Boolean
}
