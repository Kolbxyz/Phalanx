package org.phalanx.android.core.matrix

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.matrix.rustcomponents.sdk.ClientException
import org.matrix.rustcomponents.sdk.use
import org.phalanx.android.core.format.OutgoingMessage
import org.phalanx.android.core.prefs.SettingsStore
import org.phalanx.android.data.model.ScheduledMessage
import org.phalanx.android.data.repository.TimelineUnavailableException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends a scheduled message from the background, without sync: the SDK encrypts it for the room
 * and returns only once the server has it, so "sent" means delivered.
 */
@Singleton
internal class ScheduledSender @Inject constructor(
    private val active: ActiveClient,
    private val settings: SettingsStore
) {
    suspend fun send(message: ScheduledMessage): Result<Unit> = withContext(Dispatchers.IO) {
        if (active.client() == null) active.restore()
        val room = active.room(message.roomId) ?: return@withContext Result.failure(TimelineUnavailableException())
        try {
            val content = contentOf(message, settings.current().markdown)
            room.use { it.sendRaw(EVENT_TYPE, content) }
            Result.success(Unit)
        } catch (e: ClientException) {
            Result.failure(IOException("Scheduled send failed", e))
        }
    }

    /** Formatted as the composer's messages are; a reply points at the message it answers. */
    private fun contentOf(message: ScheduledMessage, formatting: Boolean): String {
        val outgoing = OutgoingMessage.of(message.text, mentions = emptyList(), formatting)
        val msgtype = when (outgoing.kind) {
            OutgoingMessage.Kind.Text -> "m.text"
            OutgoingMessage.Kind.Emote -> "m.emote"
            OutgoingMessage.Kind.Notice -> "m.notice"
        }
        return buildJsonObject {
            put("msgtype", msgtype)
            put("body", outgoing.body)
            outgoing.html?.let { html ->
                put("format", "org.matrix.custom.html")
                put("formatted_body", html)
            }
            putJsonObject("m.mentions") {
                put("user_ids", JsonArray(outgoing.userIds.map(::JsonPrimitive)))
                if (outgoing.mentionsRoom) put("room", true)
            }
            message.replyToEventId?.let { eventId ->
                putJsonObject("m.relates_to") { putJsonObject("m.in_reply_to") { put("event_id", eventId) } }
            }
        }.toString()
    }

    private companion object {
        const val EVENT_TYPE = "m.room.message"
    }
}
