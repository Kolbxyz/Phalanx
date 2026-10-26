package org.phalanx.android.ui.screens.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import org.phalanx.android.core.contacts.peopleOf
import org.phalanx.android.core.contacts.suggestLinks
import org.phalanx.android.data.model.LinkSuggestion
import org.phalanx.android.data.model.Person
import org.phalanx.android.data.repository.PeopleRepository
import org.phalanx.android.data.repository.RoomListRepository

/** The people linked across networks, and the links suggested for the user to confirm. */
data class PeopleState(val suggestions: List<LinkSuggestion>, val people: List<Person>)

@HiltViewModel
class PeopleViewModel @Inject constructor(
    roomList: RoomListRepository,
    private val repository: PeopleRepository
) : ViewModel() {
    /** Null until the rooms and the links are both loaded. */
    val state: StateFlow<PeopleState?> =
        combine(roomList.rooms.filterNotNull(), repository.links.filterNotNull()) { rooms, links ->
            PeopleState(suggestLinks(rooms, links), peopleOf(rooms, links).filter { it.chats.size > 1 })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    private val mutableFailed = MutableStateFlow(false)

    /** A change could not be saved; cleared by [dismissFailure]. */
    val failed: StateFlow<Boolean> = mutableFailed.asStateFlow()

    fun link(suggestion: LinkSuggestion) = save {
        repository.link(checkNotNull(suggestion.first.dmUserId), checkNotNull(suggestion.second.dmUserId))
    }

    fun dismiss(suggestion: LinkSuggestion) = save {
        repository.dismiss(checkNotNull(suggestion.first.dmUserId), checkNotNull(suggestion.second.dmUserId))
    }

    fun unlink(person: Person) = save { person.chats.mapNotNull { it.dmUserId }.forEach { repository.unlink(it) } }

    fun dismissFailure() {
        mutableFailed.value = false
    }

    private fun save(change: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                change()
            } catch (_: IOException) {
                mutableFailed.value = true
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
