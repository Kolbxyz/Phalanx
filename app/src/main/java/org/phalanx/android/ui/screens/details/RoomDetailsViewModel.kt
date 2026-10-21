package org.phalanx.android.ui.screens.details

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.phalanx.android.R
import org.phalanx.android.data.model.MediaRef
import org.phalanx.android.data.model.RoomSummary
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.data.repository.BlockedUsersRepository
import org.phalanx.android.data.repository.MediaRepository
import org.phalanx.android.data.repository.RoomActionsRepository
import org.phalanx.android.data.repository.RoomGallery
import org.phalanx.android.data.repository.RoomGalleryRepository
import org.phalanx.android.data.repository.RoomListRepository
import org.phalanx.android.ui.components.OpenRequest
import org.phalanx.android.ui.navigation.RoomDetailsRoute
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RoomDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    roomList: RoomListRepository,
    galleries: RoomGalleryRepository,
    private val actions: RoomActionsRepository,
    private val blockedUsers: BlockedUsersRepository,
    private val media: MediaRepository
) : ViewModel() {
    val roomId: String = savedStateHandle.toRoute<RoomDetailsRoute>().roomId

    /** Live from the room list, so a favourite or a rename shows at once. */
    val room: StateFlow<RoomSummary?> = roomList.rooms
        .map { rooms -> rooms?.firstOrNull { it.id == roomId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, roomList.rooms.value?.firstOrNull { it.id == roomId })

    private val gallery = MutableStateFlow<RoomGallery?>(null)

    val pinned: StateFlow<List<TimelineEntry.Message>?> = gallery
        .flatMapLatest { it?.pinned ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val mediaItems: StateFlow<List<TimelineEntry.Message>?> = gallery
        .flatMapLatest { it?.media ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val mutableNotice = MutableStateFlow<Int?>(null)
    val notice: StateFlow<Int?> = mutableNotice.asStateFlow()

    private val mutableOpen = MutableStateFlow<OpenRequest?>(null)
    val openRequest: StateFlow<OpenRequest?> = mutableOpen.asStateFlow()

    /** Set once the room has been left, so the screen can go back to the list. */
    private val mutableLeft = MutableStateFlow(false)
    val left: StateFlow<Boolean> = mutableLeft.asStateFlow()

    init {
        viewModelScope.launch { galleries.open(roomId).onSuccess { gallery.value = it } }
    }

    fun loadMoreMedia() {
        val current = gallery.value ?: return
        viewModelScope.launch { current.loadMoreMedia() }
    }

    fun setFavourite(favourite: Boolean) = act { actions.setFavourite(roomId, favourite) }

    fun setUnread(unread: Boolean) = act { actions.setUnread(roomId, unread) }

    fun leave() = act { actions.leave(roomId).onSuccess { mutableLeft.value = true } }

    fun block(userId: String) = act { blockedUsers.block(userId) }

    fun openFile(ref: MediaRef, filename: String, mimeType: String?) {
        viewModelScope.launch {
            media.openable(ref, filename, mimeType).fold(
                onSuccess = { mutableOpen.value = OpenRequest(it, mimeType) },
                onFailure = { notify(R.string.timeline_open_failed) }
            )
        }
    }

    fun onOpened() {
        mutableOpen.value = null
    }

    fun notify(@StringRes message: Int) {
        mutableNotice.value = message
    }

    fun dismissNotice() {
        mutableNotice.value = null
    }

    private fun act(block: suspend () -> Result<Unit>) {
        viewModelScope.launch { block().onFailure { notify(R.string.timeline_action_failed) } }
    }
}
