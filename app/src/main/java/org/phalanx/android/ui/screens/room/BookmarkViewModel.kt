package org.phalanx.android.ui.screens.room

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.phalanx.android.data.model.Bookmark
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.data.repository.BookmarkRepository
import org.phalanx.android.data.repository.RoomListRepository
import org.phalanx.android.ui.navigation.RoomRoute

/** Bookmarks and un-bookmarks this room's messages. */
@HiltViewModel
class BookmarkViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookmarks: BookmarkRepository,
    private val roomList: RoomListRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val roomId = savedStateHandle.toRoute<RoomRoute>().roomId

    /** The event ids of every bookmark, in any room. */
    val saved: StateFlow<Set<String>> = bookmarks.bookmarks
        .map { all -> all.mapTo(HashSet()) { it.eventId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptySet())

    /** Saves [message], or drops it when already saved; [onFailed] runs if the account didn't take it. */
    fun toggle(message: TimelineEntry.Message, onFailed: () -> Unit) {
        val eventId = message.eventId ?: return
        viewModelScope.launch {
            val result = if (eventId in saved.value) {
                bookmarks.remove(eventId)
            } else {
                bookmarks.add(
                    Bookmark(
                        roomId = roomId,
                        eventId = eventId,
                        roomName = roomList.rooms.value?.firstOrNull { it.id == roomId }?.name ?: message.senderName,
                        senderName = message.senderName,
                        preview = summaryOf(message.body, context::getString),
                        savedAtMillis = System.currentTimeMillis()
                    )
                )
            }
            if (result.isFailure) onFailed()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
