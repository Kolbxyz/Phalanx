package org.phalanx.android.core.matrix

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.matrix.rustcomponents.sdk.ClientException
import org.matrix.rustcomponents.sdk.MessageLikeEventContent
import org.matrix.rustcomponents.sdk.RelationType
import org.matrix.rustcomponents.sdk.TimelineEvent
import org.matrix.rustcomponents.sdk.TimelineEventContent
import org.matrix.rustcomponents.sdk.use
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.model.MessageVersion
import org.phalanx.android.data.repository.EditHistoryRepository
import org.phalanx.android.data.repository.TimelineUnavailableException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * An edit never overwrites a Matrix message: each is its own `m.replace` event, which the server
 * keeps. The SDK loads the original with them, from its cache or the server, decrypted.
 */
@Singleton
internal class RustEditHistoryRepository @Inject constructor(
    private val active: ActiveClient
) : EditHistoryRepository {
    override suspend fun versions(roomId: String, eventId: String): Result<List<MessageVersion>> =
        withContext(Dispatchers.IO) {
            val room = active.room(roomId) ?: return@withContext Result.failure(TimelineUnavailableException())
            try {
                room.use {
                    it.loadOrFetchEventWithRelations(eventId, listOf(RelationType.REPLACEMENT)).use { found ->
                        val sender = found.event.senderId()
                        // Only the sender may edit; anyone else's replacement is ignored, as the spec says.
                        val edits = found.relatedEvents
                            .filter { edit -> edit.senderId() == sender }
                            .sortedBy { edit -> edit.timestamp() }
                            .mapNotNull { edit -> edit.toVersion(isEdit = true) }
                        Result.success(listOfNotNull(found.event.toVersion(isEdit = false)) + edits)
                    }
                }
            } catch (e: ClientException) {
                Result.failure(e)
            }
        }
}

private fun TimelineEvent.toVersion(isEdit: Boolean): MessageVersion? = content().use { content ->
    val message = (content as? TimelineEventContent.MessageLike)?.content as? MessageLikeEventContent.RoomMessage
        ?: return@use null
    val body = message.messageType.toBody(isReply = message.inReplyToEventId != null)
    MessageVersion(if (isEdit) body.withoutEditMarker() else body, timestamp().toLong())
}

/**
 * The SDK reads an edit's fallback text, which clients prefix with "* " for apps that can't show
 * edits; the replacement itself never has the star.
 */
private fun MessageBody.withoutEditMarker(): MessageBody = if (this is MessageBody.Text) {
    copy(text = text.removePrefix(EDIT_MARKER), html = html?.removePrefix(EDIT_MARKER))
} else {
    this
}

private const val EDIT_MARKER = "* "
