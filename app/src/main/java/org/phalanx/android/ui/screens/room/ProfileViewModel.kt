package org.phalanx.android.ui.screens.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.phalanx.android.data.repository.RoomActionsRepository
import javax.inject.Inject

/** Starts or finds the direct chat with someone whose profile is open. */
@HiltViewModel
internal class ProfileViewModel @Inject constructor(
    private val actions: RoomActionsRepository
) : ViewModel() {
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
}
