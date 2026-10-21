package org.phalanx.android.ui.screens.roomlist

import androidx.compose.runtime.Immutable
import org.phalanx.android.data.model.RoomSummary

@Immutable
sealed interface RoomListUiState {
    /** Waiting for the SDK's first list. */
    data object Loading : RoomListUiState

    data class Loaded(val rooms: List<RoomSummary>) : RoomListUiState
}
