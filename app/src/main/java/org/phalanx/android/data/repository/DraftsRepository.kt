package org.phalanx.android.data.repository

import kotlinx.coroutines.flow.StateFlow

/** The unsent text left in rooms' composers, for the room list to show. */
interface DraftsRepository {
    /** Text by room ID, for the rooms looked up so far that hold a draft. */
    val drafts: StateFlow<Map<String, String>>

    /** Reads the saved drafts of [roomIds] not looked up yet; each room is read once. */
    fun lookUp(roomIds: Collection<String>)
}
