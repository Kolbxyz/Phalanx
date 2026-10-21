package org.phalanx.android.core.matrix

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.matrix.rustcomponents.sdk.ClientException
import org.matrix.rustcomponents.sdk.use
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
internal class ScheduledSender @Inject constructor(private val active: ActiveClient) {
    suspend fun send(message: ScheduledMessage): Result<Unit> = withContext(Dispatchers.IO) {
        if (active.client() == null) active.restore()
        val room = active.room(message.roomId) ?: return@withContext Result.failure(TimelineUnavailableException())
        try {
            room.use { it.sendRaw(EVENT_TYPE, contentOf(message)) }
            Result.success(Unit)
        } catch (e: ClientException) {
            Result.failure(IOException("Scheduled send failed", e))
        }
    }

    /** Plain text, or an emote for "/me"; a reply points at the message it answers. */
    private fun contentOf(message: ScheduledMessage): String {
        val emote = message.text.startsWith(EMOTE_PREFIX)
        return buildJsonObject {
            put("msgtype", if (emote) "m.emote" else "m.text")
            put("body", if (emote) message.text.removePrefix(EMOTE_PREFIX) else message.text)
            message.replyToEventId?.let { eventId ->
                putJsonObject("m.relates_to") { putJsonObject("m.in_reply_to") { put("event_id", eventId) } }
            }
        }.toString()
    }

    private companion object {
        const val EVENT_TYPE = "m.room.message"
        const val EMOTE_PREFIX = "/me "
    }
}
