package org.phalanx.android.ui.screens.roomlist

import androidx.compose.runtime.Immutable
import org.phalanx.android.data.model.RoomSummary

@Immutable
sealed interface RoomListUiState {
    /** Waiting for the SDK's first list. */
    data object Loading : RoomListUiState

    /**
     * [rooms] already filtered by [filter] and [source]; [archivedCount] for the Archived chip's
     * label; [sources] for the side panel, counted within [filter].
     */
    data class Loaded(
        val rooms: List<RoomSummary>,
        val filter: RoomFilter = RoomFilter.All,
        val archivedCount: Int = 0,
        val source: RoomSource = RoomSource.All,
        val sources: List<SourceCount> = emptyList(),
        /** For a row that is a linked person, all of their chats' room IDs, by the row's room ID. */
        val roomsOf: Map<String, List<String>> = emptyMap()
    ) : RoomListUiState
}
