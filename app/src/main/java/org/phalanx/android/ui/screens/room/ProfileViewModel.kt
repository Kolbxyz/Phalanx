package org.phalanx.android.ui.screens.room

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.phalanx.android.core.contacts.Conversations
import org.phalanx.android.core.contacts.peopleOf
import org.phalanx.android.data.model.Member
import org.phalanx.android.data.model.RoomSummary
import org.phalanx.android.data.model.UserTrust
import org.phalanx.android.data.repository.PeopleRepository
import org.phalanx.android.data.repository.RoomActionsRepository
import org.phalanx.android.data.repository.RoomListRepository
import org.phalanx.android.data.repository.SecurityRepository
import org.phalanx.android.ui.navigation.RoomRoute

/** Who someone whose profile is open is in this room, how far they are trusted, and a chat with them. */
@HiltViewModel
internal class ProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val actions: RoomActionsRepository,
    private val conversations: Conversations,
    private val security: SecurityRepository,
    private val roomList: RoomListRepository,
    private val people: PeopleRepository
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
        conversations.open(roomId).getOrNull()?.members(listOf(userId))?.firstOrNull() ?: Member(userId, userId, null)

    suspend fun trustOf(userId: String): UserTrust? = security.trustOf(userId)

    /** [userId]'s chats on the networks the user linked to them, their chat in this room left out. */
    fun alsoOn(userId: String): Flow<List<RoomSummary>> =
        combine(roomList.rooms.filterNotNull(), people.links.filterNotNull()) { rooms, links ->
            val person = peopleOf(rooms, links).firstOrNull { person -> person.chats.any { it.dmUserId == userId } }
            person?.chats.orEmpty().filter { it.id != roomId }
        }
}
