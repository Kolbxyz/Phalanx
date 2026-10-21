package org.phalanx.android.data.repository

import kotlinx.coroutines.flow.StateFlow
import org.phalanx.android.data.model.RoomSummary

interface RoomListRepository {
    /** Joined, non-space rooms, most recently active first. Null until the SDK's first list, and while signed out. */
    val rooms: StateFlow<List<RoomSummary>?>
}
