package org.phalanx.android.core.matrix

import org.matrix.rustcomponents.sdk.LatestEventValue
import org.matrix.rustcomponents.sdk.MessageType
import org.matrix.rustcomponents.sdk.MsgLikeKind
import org.matrix.rustcomponents.sdk.Room
import org.matrix.rustcomponents.sdk.TimelineItemContent
import org.matrix.rustcomponents.sdk.use
import org.phalanx.android.data.model.RoomSummary

/** Reads a room's current info and latest event, then releases the native [Room] handle. */
internal suspend fun Room.toSummary(): RoomSummary = use { room ->
    val info = room.roomInfo()
    val latest = room.latestEvent().use { event ->
        when (event) {
            is LatestEventValue.Remote -> LatestMessage(previewOf(event.content), event.isOwn, event.timestamp.toLong())
            is LatestEventValue.Local -> LatestMessage(previewOf(event.content), true, event.timestamp.toLong())
            is LatestEventValue.RemoteInvite -> LatestMessage(null, false, event.timestamp.toLong())
            is LatestEventValue.None -> LatestMessage(null, false, null)
        }
    }
    RoomSummary(
        id = info.id,
        name = info.displayName ?: info.rawName ?: info.id,
        isDirect = info.isDirect,
        preview = latest.text,
        previewIsOwn = latest.isOwn,
        lastActivityMillis = latest.timestampMillis,
        unreadCount = info.notificationCount.toLong(),
        highlightCount = info.highlightCount.toLong(),
        isFavourite = info.isFavourite
    )
}

private class LatestMessage(val text: String?, val isOwn: Boolean, val timestampMillis: Long?)

/** Only text-like messages get a preview; for an image the body is just a file name. */
private fun previewOf(content: TimelineItemContent): String? {
    val message = ((content as? TimelineItemContent.MsgLike)?.content?.kind as? MsgLikeKind.Message)?.content
        ?: return null
    return when (message.msgType) {
        is MessageType.Text, is MessageType.Notice, is MessageType.Emote -> message.body
        else -> null
    }
}
