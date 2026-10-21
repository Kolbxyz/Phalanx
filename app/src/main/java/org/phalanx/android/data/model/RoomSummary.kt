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
    val isFavourite: Boolean
)
