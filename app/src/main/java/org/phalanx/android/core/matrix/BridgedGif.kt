package org.phalanx.android.core.matrix

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import org.matrix.rustcomponents.sdk.EventTimelineItem
import org.phalanx.android.data.model.MessageBody

/**
 * mautrix bridges (Discord above all) send a GIF as a silent MP4 and mark it with these flags in the
 * video's info. The SDK's typed content drops unknown keys, so they are read from the event JSON;
 * an encrypted event's JSON is still the ciphertext, and its video stays an ordinary one.
 */
private val GIF_FLAGS = listOf("fi.mau.gif", "fi.mau.loop")

internal fun EventTimelineItem.withGifFlag(body: MessageBody): MessageBody =
    if (body is MessageBody.Video && videoInfoOf(this)?.let(::marksGif) == true) body.copy(isGif = true) else body

private fun videoInfoOf(item: EventTimelineItem): JsonObject? {
    val json = item.lazyProvider.debugInfo().originalJson ?: return null
    return try {
        (Json.parseToJsonElement(json) as? JsonObject)?.obj("content")?.obj("info")
    } catch (_: SerializationException) {
        null
    }
}

private fun marksGif(info: JsonObject) = GIF_FLAGS.any { (info[it] as? JsonPrimitive)?.booleanOrNull == true }

private fun JsonObject.obj(key: String) = this[key] as? JsonObject
