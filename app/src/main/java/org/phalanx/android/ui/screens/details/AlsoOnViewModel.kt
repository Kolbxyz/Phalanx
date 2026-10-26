package org.phalanx.android.ui.screens.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import org.phalanx.android.core.contacts.peopleOf
import org.phalanx.android.data.model.RoomSummary
import org.phalanx.android.data.repository.PeopleRepository
import org.phalanx.android.data.repository.RoomListRepository
import org.phalanx.android.ui.navigation.RoomDetailsRoute

/** The other chats with the person of this one-to-one chat, on other networks, as the user linked them. */
@HiltViewModel
class AlsoOnViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    roomList: RoomListRepository,
    people: PeopleRepository
) : ViewModel() {
    private val roomId = savedStateHandle.toRoute<RoomDetailsRoute>().roomId

    val others: StateFlow<List<RoomSummary>> =
        combine(roomList.rooms.filterNotNull(), people.links.filterNotNull()) { rooms, links ->
            peopleOf(rooms, links).firstOrNull { person -> person.chats.any { it.id == roomId } }
                ?.chats?.filter { it.id != roomId }.orEmpty()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
