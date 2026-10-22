package org.phalanx.android.core.matrix

import org.matrix.rustcomponents.sdk.LatestEventValue
import org.matrix.rustcomponents.sdk.Room
import org.matrix.rustcomponents.sdk.TimelineItemContent
import org.matrix.rustcomponents.sdk.use
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.model.RoomSummary
import uniffi.matrix_sdk_base.EncryptionState

/** Reads a room's current info and latest event, then releases the native [Room] handle. */
internal suspend fun Room.toSummary(): RoomSummary = use { room ->
    val info = room.roomInfo()
    val latest = room.latestEvent().use { event ->
        when (event) {
            is LatestEventValue.Remote -> LatestMessage(
                body = previewOf(event.content),
                isOwn = event.isOwn,
                sender = nameOf(event.sender, event.profile),
                timestampMillis = event.timestamp.toLong()
            )
            is LatestEventValue.Local ->
                LatestMessage(previewOf(event.content), true, null, event.timestamp.toLong())
            is LatestEventValue.RemoteInvite -> LatestMessage(null, false, null, event.timestamp.toLong())
            is LatestEventValue.None -> LatestMessage(null, false, null, null)
        }
    }
    // A DM usually has no avatar of its own; the other person's stands in, as in other clients.
    val dmHero = info.heroes.singleOrNull()?.takeIf { info.isDirect }
    val avatarUrl = info.avatarUrl ?: dmHero?.avatarUrl
    RoomSummary(
        id = info.id,
        name = info.displayName ?: info.rawName ?: info.id,
        isDirect = info.isDirect,
        avatar = mediaRefOf(avatarUrl),
        preview = latest.body,
        previewIsOwn = latest.isOwn,
        previewSender = latest.sender,
        lastActivityMillis = latest.timestampMillis,
        unreadCount = info.notificationCount.toLong(),
        highlightCount = info.highlightCount.toLong(),
        isFavourite = info.isFavourite,
        isMarkedUnread = info.isMarkedUnread,
        dmUserId = dmHero?.userId,
        topic = info.topic?.takeIf { it.isNotBlank() },
        memberCount = info.joinedMembersCount.toLong(),
        isEncrypted = info.encryptionState == EncryptionState.ENCRYPTED,
        isLowPriority = info.isLowPriority,
        pinnedEventIds = info.pinnedEventIds.toSet(),
        // The heroes are the few members a room is summed up by; bridged rooms list puppets there.
        bridge = bridgeOf(info.heroes.map { it.userId } + info.serviceMembers)
    )
}

private class LatestMessage(val body: MessageBody?, val isOwn: Boolean, val sender: String?, val timestampMillis: Long?)

private fun previewOf(content: TimelineItemContent): MessageBody? {
    val msgLike = (content as? TimelineItemContent.MsgLike)?.content ?: return null
    return msgLike.kind.toBody(isReply = msgLike.inReplyTo != null)
}
