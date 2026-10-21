package org.phalanx.android.ui.screens.room

import androidx.compose.runtime.Immutable
import org.phalanx.android.data.model.TimelineEntry

@Immutable
sealed interface RoomUiState {
    data object Loading : RoomUiState

    /** The room is unknown to this account or its timeline could not be opened. */
    data object Unavailable : RoomUiState

    data class Ready(
        val title: String,
        val isDirect: Boolean,
        /** Newest first, which is the order a reversed list lays them out in. */
        val entries: List<TimelineEntry>,
        val canLoadOlder: Boolean
    ) : RoomUiState
}
