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
import org.phalanx.android.core.contacts.Conversations
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
    private val conversations: Conversations,
    private val roomList: RoomListRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val roomId = savedStateHandle.toRoute<RoomRoute>().roomId

    /** Reminds the user of [message] at [atMillis]; [onDone] says whether it could be saved. */
    fun remind(message: TimelineEntry.Message, atMillis: Long, onDone: (Boolean) -> Unit) {
        val eventId = message.eventId ?: return onDone(false)
        viewModelScope.launch {
            // In a person's thread, the chat the message came from: that is where the reminder opens.
            val (origin, _) = conversations.originOf(roomId, message.id)
            val reminder = Reminder(
                id = UUID.randomUUID().toString(),
                roomId = origin,
                eventId = eventId,
                roomName = roomList.rooms.value?.firstOrNull { it.id == origin }?.name ?: message.senderName,
                senderName = message.senderName,
                preview = summaryOf(message.body, context::getString),
                remindAtMillis = atMillis
            )
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
