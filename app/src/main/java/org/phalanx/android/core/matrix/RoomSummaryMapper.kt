package org.phalanx.android.core.matrix

import org.matrix.rustcomponents.sdk.ClientException
import org.matrix.rustcomponents.sdk.LatestEventValue
import org.matrix.rustcomponents.sdk.Room
import org.matrix.rustcomponents.sdk.RoomInfo
import org.matrix.rustcomponents.sdk.RoomNotificationMode
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
    val heroes = info.heroes.map { RoomPerson(it.userId, it.avatarUrl) }.ifEmpty { room.smallRoomMembers(info) }
    val people = info.people(heroes)
    val dmHero = people.singleOrNull()
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
        // The server's counts miss encrypted rooms (it can't read them) and rooms not set to notify
        // on every message; the SDK's own counts cover both.
        unreadCount = maxOf(info.numUnreadMessages, info.notificationCount).toLong(),
        highlightCount = maxOf(info.numUnreadMentions, info.highlightCount).toLong(),
        isFavourite = info.isFavourite,
        isMarkedUnread = info.isMarkedUnread,
        dmUserId = dmHero?.userId,
        dmCandidates = people.map { it.userId },
        topic = info.topic?.takeIf { it.isNotBlank() },
        memberCount = info.joinedMembersCount.toLong(),
        isEncrypted = info.encryptionState == EncryptionState.ENCRYPTED,
        isLowPriority = info.isLowPriority,
        pinnedEventIds = info.pinnedEventIds.toSet(),
        bridge = info.bridge(heroes),
        isMuted = info.cachedUserDefinedNotificationMode == RoomNotificationMode.MUTE
    )
}

// Without the user, as the server's heroes are: a named chat of two would otherwise count two
// people in it, and be with no one.
private suspend fun Room.smallRoomMembers(info: RoomInfo): List<RoomPerson> {
    if (info.joinedMembersCount.toLong() > SMALL_ROOM_MEMBERS) return emptyList()
    return try {
        val own = ownUserId()
        membersNoSync().use { it.nextChunk(SMALL_ROOM_MEMBERS.toUInt()).orEmpty() }
            .filter { it.userId != own }
            .map { RoomPerson(it.userId, it.avatarUrl) }
    } catch (_: ClientException) {
        // Members not stored yet; the next update of the room asks again.
        emptyList()
    }
}

// The user, their own bridged account, the person brought over and the bridge's bot.
private const val SMALL_ROOM_MEMBERS = 4L

private class LatestMessage(val body: MessageBody?, val isOwn: Boolean, val sender: String?, val timestampMillis: Long?)

private fun previewOf(content: TimelineItemContent): MessageBody? {
    val msgLike = (content as? TimelineItemContent.MsgLike)?.content ?: return null
    return msgLike.kind.toBody(isReply = msgLike.inReplyTo != null)
}
