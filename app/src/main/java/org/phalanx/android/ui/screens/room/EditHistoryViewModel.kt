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
import org.phalanx.android.data.model.MessageVersion
import org.phalanx.android.data.repository.EditHistoryRepository
import org.phalanx.android.ui.navigation.RoomRoute

sealed interface EditHistoryState {
    data object Loading : EditHistoryState

    data class Loaded(val versions: List<MessageVersion>) : EditHistoryState

    data object Failed : EditHistoryState
}

/** One message's versions; scoped to the sheet by the message's event ID. */
@HiltViewModel
class EditHistoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val history: EditHistoryRepository
) : ViewModel() {
    private val roomId = savedStateHandle.toRoute<RoomRoute>().roomId
    private val mutableState = MutableStateFlow<EditHistoryState>(EditHistoryState.Loading)
    val state: StateFlow<EditHistoryState> = mutableState.asStateFlow()
    private var requested: String? = null

    /** Loads once per event; a configuration change keeps what was already fetched. */
    fun load(eventId: String) {
        if (requested == eventId) return
        requested = eventId
        viewModelScope.launch {
            mutableState.value = history.versions(roomId, eventId).fold(
                onSuccess = { EditHistoryState.Loaded(it) },
                onFailure = { EditHistoryState.Failed }
            )
        }
    }
}
