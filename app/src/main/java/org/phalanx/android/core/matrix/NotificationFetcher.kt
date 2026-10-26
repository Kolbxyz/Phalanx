package org.phalanx.android.core.matrix

import android.graphics.BitmapFactory
import javax.inject.Inject
import javax.inject.Singleton
import org.matrix.rustcomponents.sdk.Client
import org.matrix.rustcomponents.sdk.ClientException
import org.matrix.rustcomponents.sdk.MediaSource
import org.matrix.rustcomponents.sdk.MessageLikeEventContent
import org.matrix.rustcomponents.sdk.NotificationEvent
import org.matrix.rustcomponents.sdk.NotificationItem
import org.matrix.rustcomponents.sdk.NotificationProcessSetup
import org.matrix.rustcomponents.sdk.NotificationSettingsException
import org.matrix.rustcomponents.sdk.NotificationStatus
import org.matrix.rustcomponents.sdk.RoomNotificationMode
import org.matrix.rustcomponents.sdk.TimelineEventContent
import org.matrix.rustcomponents.sdk.use
import org.phalanx.android.core.prefs.SettingsStore
import org.phalanx.android.data.model.IncomingMessage
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.model.NotificationPicture
import org.phalanx.android.data.model.UnsupportedKind

/** Turns a push's event ID into a message, through the SDK's short notification sync and local decryption. */
@Singleton
internal class NotificationFetcher @Inject constructor(
    private val active: ActiveClient,
    private val settings: SettingsStore,
    private val mutes: MuteStates
) {
    /**
     * Null when there is nothing to show: the account signed out, the push rules filter the event,
     * or it was redacted or is a kind that never notifies. A failure is worth retrying.
     */
    suspend fun fetch(roomId: String, eventId: String): Result<IncomingMessage?> {
        // A push can start the process cold, before any screen has restored the session.
        active.restore()
        val client = active.client() ?: return Result.success(null)
        val sync = active.syncService.value ?: return Result.success(null)
        val current = settings.current()
        val pictures = current.autoLoadMedia
        val reactions = current.reactionNotifications
        if (isMuted(client, roomId)) return Result.success(null)
        return try {
            // Not destroyed: SingleProcess borrows the sync service, which the active client owns.
            val setup = NotificationProcessSetup.SingleProcess(sync)
            val message = client.notificationClient(setup).use { notifications ->
                when (val status = notifications.getNotification(roomId, eventId)) {
                    is NotificationStatus.Event -> incomingOf(client, roomId, eventId, status.item, reactions)
                        ?.let {
                            it.copy(
                                senderAvatar = client.avatarBitmap(status.item.senderInfo.avatarUrl),
                                picture = if (pictures) it.body?.let { body -> client.pictureOf(body) } else null
                            )
                        }
                        .also { status.destroy() }
                    NotificationStatus.EventNotFound,
                    NotificationStatus.EventFilteredOut,
                    NotificationStatus.EventRedacted -> null
                }
            }
            Result.success(message)
        } catch (e: ClientException) {
            Result.failure(e)
        }
    }

    // A reaction notifies only when the user asked for it here, and only for their own messages.
    private suspend fun incomingOf(
        client: Client,
        roomId: String,
        eventId: String,
        item: NotificationItem,
        reactions: Boolean
    ): IncomingMessage? {
        val reaction = item.reaction() ?: return item.toIncoming(roomId, eventId)
        return if (reactions) client.reactionToOwn(roomId, eventId, item, reaction) else null
    }

    /** A room muted here, or by the account's push rules elsewhere, never notifies on this device. */
    private suspend fun isMuted(client: Client, roomId: String): Boolean {
        mutes.byRoom.value[roomId]?.let { return it }
        return try {
            client.getNotificationSettings().use { it.getUserDefinedRoomNotificationMode(roomId) } ==
                RoomNotificationMode.MUTE
        } catch (_: NotificationSettingsException) {
            // Unknown: better a notification too many than a message missed.
            false
        }
    }
}

private fun NotificationItem.toIncoming(roomId: String, eventId: String): IncomingMessage? {
    val body: MessageBody?
    val sender: String
    val timestamp: Long
    when (val notified = event) {
        is NotificationEvent.Timeline -> {
            val content = (notified.event.content() as? TimelineEventContent.MessageLike)?.content
            body = when (content) {
                is MessageLikeEventContent.RoomMessage ->
                    content.messageType.toBody(isReply = content.inReplyToEventId != null)
                MessageLikeEventContent.RoomEncrypted -> MessageBody.Undecryptable
                is MessageLikeEventContent.Poll -> MessageBody.Unsupported(UnsupportedKind.Poll)
                else -> return null
            }
            sender = senderInfo.displayName ?: notified.event.senderId()
            timestamp = notified.event.timestamp().toLong()
        }
        is NotificationEvent.Invite -> {
            body = null
            sender = senderInfo.displayName ?: notified.sender
            timestamp = System.currentTimeMillis()
        }
    }
    return IncomingMessage(
        roomId = roomId,
        eventId = eventId,
        roomName = roomInfo.displayName,
        isDirect = roomInfo.isDm,
        senderName = sender,
        body = body,
        timestampMillis = timestamp,
        isNoisy = isNoisy ?: false
    )
}

/** The sender's photo for the notification; best effort, the system draws their initial without one. */
private suspend fun Client.avatarBitmap(url: String?) = url?.let {
    try {
        val bytes = MediaSource.fromUrl(it).use { source ->
            getMediaThumbnail(source, AVATAR_PX.toULong(), AVATAR_PX.toULong())
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (_: ClientException) {
        null
    }
}

/**
 * A photo's (or a video's) server thumbnail, for the notification to show; best effort. A GIF's
 * is a still frame: notifications draw no animation.
 */
private suspend fun Client.pictureOf(body: MessageBody): NotificationPicture? {
    val (ref, mimeType) = when (body) {
        is MessageBody.Image -> (body.thumbnail ?: body.media) to (body.mimeType ?: JPEG)
        is MessageBody.Video -> body.thumbnail to JPEG
        else -> null to JPEG
    }
    if (ref == null) return null
    return try {
        val bytes = ref.toSource().use { getMediaThumbnail(it, PICTURE_PX.toULong(), PICTURE_PX.toULong()) }
        // The server picks the thumbnail's format; the system reads the bytes, not this label.
        NotificationPicture(bytes, mimeType.takeIf { it.startsWith("image/") } ?: JPEG)
    } catch (_: ClientException) {
        null
    }
}

// Wide enough for an expanded notification on any phone, small enough to fetch on every push.
private const val PICTURE_PX = 640
private const val JPEG = "image/jpeg"

// The 48 dp conversation avatar at up to 3x density.
private const val AVATAR_PX = 144
