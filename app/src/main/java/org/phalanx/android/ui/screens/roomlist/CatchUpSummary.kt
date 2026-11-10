package org.phalanx.android.ui.screens.roomlist

import androidx.compose.runtime.Immutable
import org.phalanx.android.core.ai.roomsToCatchUp
import org.phalanx.android.data.model.RoomSummary

/** What a catch-up would read: [unread] messages over [chats] chats, [mentions] of them naming the user. */
@Immutable
data class CatchUpSummary(val unread: Long, val chats: Int, val mentions: Long)

/** Null when there is nothing a catch-up would read. */
internal fun catchUpOf(rooms: List<RoomSummary>): CatchUpSummary? {
    val toRead = roomsToCatchUp(rooms, System.currentTimeMillis())
    if (toRead.isEmpty()) return null
    return CatchUpSummary(
        unread = toRead.sumOf { it.unreadCount },
        chats = toRead.size,
        mentions = toRead.sumOf { it.highlightCount }
    )
}
