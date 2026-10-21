package org.phalanx.android.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class RoomSummary(
    val id: String,
    val name: String,
    val isDirect: Boolean,
    /** Plain-text preview of the latest message; null when the room has none or it is not text. */
    val preview: String?,
    /** Whether [preview] was sent by this account, so the row can prefix it. */
    val previewIsOwn: Boolean,
    val lastActivityMillis: Long?,
    val unreadCount: Long,
    val highlightCount: Long,
    val isFavourite: Boolean
)
