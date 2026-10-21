package org.phalanx.android.ui.screens.roomlist

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.phalanx.android.data.model.RoomSummary
import org.phalanx.android.data.repository.AccountProfile
import org.phalanx.android.data.repository.AccountRepository
import org.phalanx.android.data.repository.RoomListRepository
import org.phalanx.android.ui.components.decodeAvatar
import javax.inject.Inject

@HiltViewModel
class RoomListViewModel @Inject constructor(
    roomList: RoomListRepository,
    account: AccountRepository
) : ViewModel() {
    // Starts from the repository's current list, so coming back to this screen never flashes a
    // loading state.
    val state: StateFlow<RoomListUiState> = roomList.rooms
        .map(::toUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), toUiState(roomList.rooms.value))

    /** For the account button in the top bar. */
    val profile: StateFlow<AccountProfile?> = account.profile

    val avatar: StateFlow<ImageBitmap?> = account.avatar(AVATAR_PX)
        .map(::decodeAvatar)
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    private fun toUiState(rooms: List<RoomSummary>?): RoomListUiState =
        rooms?.let { RoomListUiState.Loaded(it) } ?: RoomListUiState.Loading

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        // 32 dp at up to 3x density.
        const val AVATAR_PX = 96
    }
}
