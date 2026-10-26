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
import org.phalanx.android.data.model.Member
import org.phalanx.android.data.model.UserTrust
import org.phalanx.android.data.repository.RoomActionsRepository
import org.phalanx.android.data.repository.SecurityRepository
import org.phalanx.android.data.repository.TimelineRepository
import org.phalanx.android.ui.navigation.RoomRoute

/** Who someone whose profile is open is in this room, how far they are trusted, and a chat with them. */
@HiltViewModel
internal class ProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val actions: RoomActionsRepository,
    private val timelines: TimelineRepository,
    private val security: SecurityRepository
) : ViewModel() {
    private val roomId = savedStateHandle.toRoute<RoomRoute>().roomId

    /** The user a direct chat is being opened with, so the action can't be pressed twice. */
    private val mutableOpening = MutableStateFlow<String?>(null)
    val opening: StateFlow<String?> = mutableOpening.asStateFlow()

    fun message(userId: String, onOpened: (roomId: String) -> Unit, onFailed: () -> Unit) {
        if (mutableOpening.value != null) return
        mutableOpening.value = userId
        viewModelScope.launch {
            actions.directRoomWith(userId).fold(onSuccess = onOpened, onFailure = { onFailed() })
            mutableOpening.value = null
        }
    }

    /** Who [userId] is in this room; just their ID when the room doesn't know them. */
    suspend fun member(userId: String): Member =
        timelines.open(roomId).getOrNull()?.members(listOf(userId))?.firstOrNull() ?: Member(userId, userId, null)

    suspend fun trustOf(userId: String): UserTrust? = security.trustOf(userId)
}
