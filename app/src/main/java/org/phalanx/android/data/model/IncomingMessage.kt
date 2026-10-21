package org.phalanx.android.data.model

/** One event a push woke the app for, fetched and decrypted on the device. */
data class IncomingMessage(
    val roomId: String,
    val eventId: String,
    val roomName: String,
    val isDirect: Boolean,
    val senderName: String,
    /** Null for an invitation to the room. */
    val body: MessageBody?,
    val timestampMillis: Long,
    /** The account's push rules ask for a sound. */
    val isNoisy: Boolean
)
