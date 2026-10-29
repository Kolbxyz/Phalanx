package org.phalanx.android.ui.screens.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.phalanx.android.core.contacts.linkKey
import org.phalanx.android.core.contacts.peopleOf
import org.phalanx.android.data.model.RoomSummary
import org.phalanx.android.data.repository.PeopleRepository
import org.phalanx.android.data.repository.RoomListRepository
import org.phalanx.android.ui.navigation.RoomDetailsRoute

/**
 * This chat's conversation: the same person's or group's chats on other networks, as the user
 * linked them, and every other chat the user can link to it by hand.
 */
data class AlsoOnState(val others: List<RoomSummary>, val candidates: List<RoomSummary>)

@HiltViewModel
class AlsoOnViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val roomList: RoomListRepository,
    private val people: PeopleRepository
) : ViewModel() {
    private val roomId = savedStateHandle.toRoute<RoomDetailsRoute>().roomId

    val state: StateFlow<AlsoOnState> =
        combine(roomList.rooms.filterNotNull(), people.links.filterNotNull()) { rooms, links ->
            val everyone = peopleOf(rooms, links)
            val mine = everyone.firstOrNull { person -> person.chats.any { it.id == roomId } }?.chats.orEmpty()
            AlsoOnState(
                others = mine.filter { it.id != roomId },
                candidates = everyone.flatMap { it.chats }.filter { chat -> mine.none { it.id == chat.id } }
                    .sortedBy { it.name.lowercase() }
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            AlsoOnState(emptyList(), emptyList())
        )

    private val mutableFailed = MutableStateFlow(false)

    /** The last link could not be saved. */
    val failed: StateFlow<Boolean> = mutableFailed.asStateFlow()

    /** Makes [other]'s conversation and this chat's one. */
    fun link(other: RoomSummary) {
        val mine = roomList.rooms.value?.firstOrNull { it.id == roomId } ?: return
        viewModelScope.launch {
            mutableFailed.value = try {
                people.link(linkKey(mine), linkKey(other))
                false
            } catch (_: IOException) {
                true
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
