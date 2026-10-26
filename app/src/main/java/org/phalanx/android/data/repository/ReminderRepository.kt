package org.phalanx.android.data.repository

import kotlinx.coroutines.flow.Flow
import org.phalanx.android.data.model.Reminder

/**
 * Messages to be reminded of at a chosen time, by a notification that opens the conversation on
 * them. Kept on this phone, even with the app closed or after a restart.
 */
interface ReminderRepository {
    /** Every reminder still to come, soonest first. */
    val reminders: Flow<List<Reminder>>

    suspend fun remind(reminder: Reminder)

    suspend fun cancel(id: String)
}
