package org.phalanx.android.data.model

import android.graphics.Bitmap

/** One event a push woke the app for, fetched and decrypted on the device. */
data class IncomingMessage(
    val roomId: String,
    val eventId: String,
    val roomName: String,
    val isDirect: Boolean,
    val senderName: String,
    /** Their photo, already fetched: a notification can't load it later. Null without one. */
    val senderAvatar: Bitmap? = null,
    /** Null for an invitation to the room. */
    val body: MessageBody?,
    /** The photo (or a video's still) the message carries, when media loads automatically. */
    val picture: NotificationPicture? = null,
    val timestampMillis: Long,
    /** The account's push rules ask for a sound. */
    val isNoisy: Boolean
)
