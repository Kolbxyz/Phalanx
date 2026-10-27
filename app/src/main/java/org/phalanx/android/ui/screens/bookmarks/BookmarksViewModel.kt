package org.phalanx.android.ui.screens.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.phalanx.android.data.model.Bookmark
import org.phalanx.android.data.repository.BookmarkRepository
import org.phalanx.android.data.repository.RoomListRepository

/** The saved messages, brought up to date with the account when the screen opens, and removing one. */
@HiltViewModel
class BookmarksViewModel @Inject constructor(
    private val repository: BookmarkRepository,
    roomList: RoomListRepository
) : ViewModel() {
    private val mutableFailed = MutableStateFlow(false)

    /** True when the account couldn't be read or written, so the list may be out of date. */
    val failed: StateFlow<Boolean> = mutableFailed

    /** Newest first. A bookmark made on another device has no room name yet: the room list supplies it. */
    val bookmarks: StateFlow<List<Bookmark>> = combine(repository.bookmarks, roomList.rooms) { saved, rooms ->
        saved.map { bookmark ->
            if (bookmark.roomName.isNotBlank()) {
                bookmark
            } else {
                bookmark.copy(roomName = rooms?.firstOrNull { it.id == bookmark.roomId }?.name.orEmpty())
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    init {
        viewModelScope.launch { mutableFailed.value = repository.refresh().isFailure }
    }

    fun remove(eventId: String) {
        viewModelScope.launch { mutableFailed.value = repository.remove(eventId).isFailure }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
