package org.phalanx.android.data.repository

/** What can be done to a whole room from the room list or its details, without opening it. */
interface RoomActionsRepository {
    suspend fun setFavourite(roomId: String, favourite: Boolean): Result<Unit>

    /** Unread puts the flag back on; read clears it and sends a read receipt for the latest message. */
    suspend fun setUnread(roomId: String, unread: Boolean): Result<Unit>

    suspend fun leave(roomId: String): Result<Unit>

    /** Archives or restores the room, through the low-priority tag every Matrix client shares. */
    suspend fun setArchived(roomId: String, archived: Boolean): Result<Unit>
}
