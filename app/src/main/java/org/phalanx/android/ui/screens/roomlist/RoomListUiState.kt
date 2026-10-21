package org.phalanx.android.ui.screens.roomlist

import androidx.compose.runtime.Immutable
import org.phalanx.android.data.model.RoomSummary

@Immutable
sealed interface RoomListUiState {
    /** Waiting for the SDK's first list. */
    data object Loading : RoomListUiState

    /** [rooms] already filtered by [filter]; [archivedCount] for the Archived chip's label. */
    data class Loaded(
        val rooms: List<RoomSummary>,
        val filter: RoomFilter = RoomFilter.All,
        val archivedCount: Int = 0
    ) : RoomListUiState
}
