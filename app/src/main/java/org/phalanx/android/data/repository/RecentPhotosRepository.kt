package org.phalanx.android.data.repository

import org.phalanx.android.data.model.MessageBody

/** A photo sent in a room, as the photo index needs it. */
data class PhotoRef(val roomId: String, val eventId: String, val image: MessageBody.Image, val sentAtMillis: Long)

/** The photos of every joined room, for indexing them in the background. */
interface RecentPhotosRepository {
    /**
     * Up to [perRoom] of each joined room's newest photos, stickers and GIFs left out. Works without
     * a running sync, from what the phone stored and what the server returns when paged back.
     */
    suspend fun recent(perRoom: Int): List<PhotoRef>
}
