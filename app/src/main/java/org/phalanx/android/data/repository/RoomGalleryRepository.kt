package org.phalanx.android.data.repository

import kotlinx.coroutines.flow.StateFlow
import org.phalanx.android.data.model.Participant
import org.phalanx.android.data.model.TimelineEntry

/** A room's pinned messages, shared media and members, for the conversation details screen. */
interface RoomGalleryRepository {
    /** Opens [roomId]'s gallery; the one opened before, for another room, is released. */
    suspend fun open(roomId: String): Result<RoomGallery>

    /** The gallery opened last, if it is [roomId]'s, so the media viewer can find its items. */
    fun current(roomId: String): RoomGallery?
}

interface RoomGallery {
    /** Oldest first, as the room pinned them. */
    val pinned: StateFlow<List<TimelineEntry.Message>>

    /** Photos, videos, files and audio, newest first. */
    val media: StateFlow<List<TimelineEntry.Message>>

    val hasMoreMedia: StateFlow<Boolean>

    /** Loads one more page of older media. */
    suspend fun loadMoreMedia()

    /** Joined and invited members, highest role first, then by name. Empty if they can't be loaded. */
    suspend fun members(): List<Participant>
}
