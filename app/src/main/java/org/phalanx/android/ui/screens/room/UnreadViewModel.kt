package org.phalanx.android.ui.screens.room

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.phalanx.android.data.repository.RoomListRepository
import org.phalanx.android.data.repository.TimelineRepository
import org.phalanx.android.ui.navigation.RoomRoute

/**
 * Offers a jump to the first message unread when the room was opened, when there were too many
 * to see without scrolling. History is paged back only when the user asks for the jump.
 */
@HiltViewModel
class UnreadViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val timelines: TimelineRepository,
    roomList: RoomListRepository
) : ViewModel() {
    private val roomId = savedStateHandle.toRoute<RoomRoute>().roomId
    private val unreadCount = roomList.rooms.value?.firstOrNull { it.id == roomId }?.unreadCount ?: 0L
    private val readAtOpen = ReadAtOpen(viewModelScope, timelines, roomId, unreadCount)

    /** How many messages were unread at open, while the jump is offered; null once it is not. */
    private val mutableOffer = MutableStateFlow(unreadCount.takeIf { it >= MIN_UNREAD }?.toInt())
    val offer: StateFlow<Int?> = mutableOffer.asStateFlow()

    /** Finds the first unread message, then hands its event to [onFound], or calls [onMissing]. */
    fun jump(onFound: (String) -> Unit, onMissing: () -> Unit) {
        mutableOffer.value = null
        viewModelScope.launch {
            val session = timelines.open(roomId).getOrNull()
            val eventId = session?.let { readAtOpen.firstUnreadEventId(it) }
            if (eventId != null) onFound(eventId) else onMissing()
        }
    }

    fun dismiss() {
        mutableOffer.value = null
    }

    private companion object {
        // About a screenful: fewer are all in view when the room opens at its newest message.
        const val MIN_UNREAD = 8L
    }
}
