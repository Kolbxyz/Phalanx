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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.phalanx.android.core.contacts.Conversations
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.ui.navigation.RoomRoute

/** Who reacted with one key to one message, while that list is open; members null while loading. */
@HiltViewModel
class ReactorsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val conversations: Conversations
) : ViewModel() {
    private val roomId = savedStateHandle.toRoute<RoomRoute>().roomId

    private val mutableReactors = MutableStateFlow<Reactors?>(null)
    val reactors: StateFlow<Reactors?> = mutableReactors.asStateFlow()

    /** Opens on [key]'s reactors, with every other reaction to the message a tab away. */
    fun show(message: TimelineEntry.Message, key: String) {
        if (message.reactions.none { it.key == key }) return
        val opened = Reactors(message.reactions, key, members = null)
        mutableReactors.value = opened
        viewModelScope.launch {
            val session = conversations.open(roomId).getOrNull() ?: return@launch
            val members = session.members(message.reactions.flatMap { it.senderIds }.distinct())
            // Unless the user closed it, or opened another, while the names loaded.
            mutableReactors.update {
                if (it === opened) opened.copy(members = members.associateBy { m -> m.userId }) else it
            }
        }
    }

    fun dismiss() {
        mutableReactors.value = null
    }
}
