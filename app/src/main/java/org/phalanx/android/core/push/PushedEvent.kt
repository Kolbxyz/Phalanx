package org.phalanx.android.core.push

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** The event a push points at. The push carries nothing else: the message is fetched and decrypted here. */
internal data class PushedEvent(val roomId: String, val eventId: String)

@Serializable
private class Envelope(val notification: Payload)

@Serializable
private class Payload(
    @SerialName("room_id") val roomId: String? = null,
    @SerialName("event_id") val eventId: String? = null
)

private val json = Json { ignoreUnknownKeys = true }

/**
 * Reads the Matrix push the gateway forwarded. Null for anything without an event to show, such as
 * an unread-count update, and for bytes that are not a Matrix push at all.
 */
internal fun parsePush(content: ByteArray): PushedEvent? {
    val payload = try {
        json.decodeFromString(Envelope.serializer(), content.decodeToString()).notification
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
    val roomId = payload?.roomId
    val eventId = payload?.eventId
    return if (roomId != null && eventId != null) PushedEvent(roomId, eventId) else null
}
