package org.phalanx.android.core.ai

import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.model.RoomSummary

/**
 * The rooms to catch up on, most worth reading first, without running any model: mentions of the
 * user, then one-to-one chats, a question left for them, how much is unread and how recent it is.
 * Muted and archived rooms are left out; the user chose not to hear from them.
 */
fun roomsToCatchUp(rooms: List<RoomSummary>, nowMillis: Long): List<RoomSummary> = rooms
    .filter { it.hasUnread && !it.isMuted && !it.isLowPriority }
    .sortedByDescending { scoreOf(it, nowMillis) }

private fun scoreOf(room: RoomSummary, nowMillis: Long): Long {
    val hoursAgo = room.lastActivityMillis?.let { (nowMillis - it) / HOUR_MILLIS } ?: DAY_HOURS
    val text = (room.preview as? MessageBody.Text)?.text
    val asksYou = !room.previewIsOwn && text?.trimEnd()?.endsWith('?') == true
    return room.highlightCount.coerceAtMost(MAX_COUNTED) * MENTION +
        (if (room.isDirect) DIRECT else 0) +
        (if (asksYou) QUESTION else 0) +
        (if (room.isFavourite) FAVOURITE else 0) +
        room.unreadCount.coerceAtMost(MAX_COUNTED) +
        (DAY_HOURS - hoursAgo).coerceAtLeast(0)
}

private const val HOUR_MILLIS = 3_600_000L
private const val DAY_HOURS = 24L
private const val MAX_COUNTED = 50L
private const val MENTION = 100L
private const val DIRECT = 60L
private const val QUESTION = 30L
private const val FAVOURITE = 15L
