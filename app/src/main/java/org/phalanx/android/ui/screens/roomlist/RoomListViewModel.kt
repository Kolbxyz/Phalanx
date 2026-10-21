package org.phalanx.android.ui.screens.roomlist

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.phalanx.android.data.model.RoomSummary
import org.phalanx.android.data.repository.AccountProfile
import org.phalanx.android.data.repository.AccountRepository
import org.phalanx.android.data.repository.RoomActionsRepository
import org.phalanx.android.data.repository.RoomListRepository
import org.phalanx.android.ui.components.decodeAvatar
import javax.inject.Inject

@HiltViewModel
class RoomListViewModel @Inject constructor(
    roomList: RoomListRepository,
    account: AccountRepository,
    private val actions: RoomActionsRepository
) : ViewModel() {
    // Starts from the repository's current list, so coming back to this screen never flashes a
    // loading state.
    private val filter = MutableStateFlow(RoomFilter.All)

    val state: StateFlow<RoomListUiState> = combine(roomList.rooms, filter, ::toUiState)
        .flowOn(Dispatchers.Default)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            toUiState(roomList.rooms.value, filter.value)
        )

    /** For the account button in the top bar. */
    val profile: StateFlow<AccountProfile?> = account.profile

    val avatar: StateFlow<ImageBitmap?> = account.avatar(AVATAR_PX)
        .map(::decodeAvatar)
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** Set when a room action failed; cleared once shown. */
    private val mutableFailed = MutableStateFlow(false)
    val actionFailed: StateFlow<Boolean> = mutableFailed.asStateFlow()

    fun setFavourite(room: RoomSummary, favourite: Boolean) = act { actions.setFavourite(room.id, favourite) }

    fun setUnread(room: RoomSummary, unread: Boolean) = act { actions.setUnread(room.id, unread) }

    fun leave(room: RoomSummary) = act { actions.leave(room.id) }

    fun setArchived(room: RoomSummary, archived: Boolean) = act { actions.setArchived(room.id, archived) }

    fun setFilter(value: RoomFilter) {
        filter.value = value
    }

    fun dismissFailure() {
        mutableFailed.value = false
    }

    private fun act(block: suspend () -> Result<Unit>) {
        viewModelScope.launch { block().onFailure { mutableFailed.value = true } }
    }

    private fun toUiState(rooms: List<RoomSummary>?, filter: RoomFilter): RoomListUiState {
        rooms ?: return RoomListUiState.Loading
        val now = System.currentTimeMillis()
        return RoomListUiState.Loaded(
            rooms = rooms.filter { filter.matches(it, now) },
            filter = filter,
            archivedCount = rooms.count { it.isArchived(now) }
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        // 32 dp at up to 3x density.
        const val AVATAR_PX = 96
    }
}
