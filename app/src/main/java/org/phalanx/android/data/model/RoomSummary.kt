package org.phalanx.android.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class RoomSummary(
    val id: String,
    val name: String,
    val isDirect: Boolean,
    /** The room's avatar, or for a DM the other person's. */
    val avatar: MediaRef?,
    /** The latest message; null when the room has none that can be shown. */
    val preview: MessageBody?,
    /** Whether [preview] was sent by this account, so the row can prefix it. */
    val previewIsOwn: Boolean,
    /** Who sent [preview], for group chats. */
    val previewSender: String?,
    val lastActivityMillis: Long?,
    val unreadCount: Long,
    val highlightCount: Long,
    val isFavourite: Boolean,
    /** Marked unread by hand, here or on another device; shown like a room with new messages. */
    val isMarkedUnread: Boolean = false,
    /** The other person's Matrix ID in a one-to-one chat, so they can be blocked from it. */
    val dmUserId: String? = null,
    val topic: String? = null,
    val memberCount: Long = 0,
    val isEncrypted: Boolean = false
) {
    val hasUnread: Boolean
        get() = unreadCount > 0 || isMarkedUnread
}
