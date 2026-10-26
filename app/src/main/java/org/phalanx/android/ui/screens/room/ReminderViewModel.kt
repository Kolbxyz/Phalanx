package org.phalanx.android.ui.screens.room

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.phalanx.android.data.model.Reminder
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.data.repository.ReminderRepository
import org.phalanx.android.data.repository.RoomListRepository
import org.phalanx.android.ui.navigation.RoomRoute

/** Sets reminders on this room's messages. */
@HiltViewModel
class ReminderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val reminders: ReminderRepository,
    private val roomList: RoomListRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val roomId = savedStateHandle.toRoute<RoomRoute>().roomId

    /** Reminds the user of [message] at [atMillis]; [onDone] says whether it could be saved. */
    fun remind(message: TimelineEntry.Message, atMillis: Long, onDone: (Boolean) -> Unit) {
        val eventId = message.eventId ?: return onDone(false)
        val roomName = roomList.rooms.value?.firstOrNull { it.id == roomId }?.name ?: message.senderName
        val reminder = Reminder(
            id = UUID.randomUUID().toString(),
            roomId = roomId,
            eventId = eventId,
            roomName = roomName,
            senderName = message.senderName,
            preview = summaryOf(message.body, context::getString),
            remindAtMillis = atMillis
        )
        viewModelScope.launch {
            val saved = try {
                reminders.remind(reminder)
                true
            } catch (_: IOException) {
                false
            }
            onDone(saved)
        }
    }
}
