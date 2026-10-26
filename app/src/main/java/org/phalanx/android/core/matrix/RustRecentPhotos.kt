package org.phalanx.android.core.matrix

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.matrix.rustcomponents.sdk.ClientException
import org.matrix.rustcomponents.sdk.DateDividerMode
import org.matrix.rustcomponents.sdk.Membership
import org.matrix.rustcomponents.sdk.Room
import org.matrix.rustcomponents.sdk.RoomMessageEventMessageType
import org.matrix.rustcomponents.sdk.TimelineConfiguration
import org.matrix.rustcomponents.sdk.TimelineDiff
import org.matrix.rustcomponents.sdk.TimelineFilter
import org.matrix.rustcomponents.sdk.TimelineFocus
import org.matrix.rustcomponents.sdk.TimelineListener
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.data.repository.PhotoRef
import org.phalanx.android.data.repository.RecentPhotosRepository
import uniffi.matrix_sdk_ui.TimelineReadReceiptTracking

/** Each joined room's photos through a timeline of its own, filtered to images, opened and closed per room. */
@Singleton
internal class RustRecentPhotos @Inject constructor(private val active: ActiveClient) : RecentPhotosRepository {
    override suspend fun recent(perRoom: Int): List<PhotoRef> {
        active.restore()
        val client = active.client() ?: return emptyList()
        val rooms = withContext(Dispatchers.IO) { client.rooms() }
        return rooms.flatMap { room ->
            try {
                if (room.membership() == Membership.JOINED) photosOf(room, perRoom) else emptyList()
            } finally {
                room.destroy()
            }
        }
    }

    private suspend fun photosOf(room: Room, perRoom: Int): List<PhotoRef> = withContext(Dispatchers.IO) {
        val items = TimelineItems(MappingContext(room.ownUserId()) { })
        val timeline = room.timelineWithConfiguration(CONFIGURATION)
        val handle = timeline.addListener(object : TimelineListener {
            override fun onUpdate(diff: List<TimelineDiff>) = items.apply(diff)
        })
        try {
            var pages = 0
            var atStart = false
            while (!atStart && pages < MAX_PAGES && photosIn(room.id(), items).size < perRoom) {
                atStart = timeline.paginateBackwards(PAGE_SIZE.toUShort())
                pages++
            }
        } catch (_: ClientException) {
            // Offline or a server hiccup: what was already paged in is indexed, the rest next time.
        } finally {
            handle.cancel()
            handle.destroy()
            timeline.destroy()
        }
        photosIn(room.id(), items).take(perRoom)
    }

    private fun photosIn(roomId: String, items: TimelineItems): List<PhotoRef> =
        items.entries.value.filterIsInstance<TimelineEntry.Message>().asReversed().mapNotNull { message ->
            val image = (message.body as? MessageBody.Image)?.takeUnless { it.isSticker || it.mimeType == GIF }
            val eventId = message.eventId
            if (image != null && eventId != null) PhotoRef(roomId, eventId, image, message.timestampMillis) else null
        }

    private companion object {
        const val PAGE_SIZE = 40
        const val MAX_PAGES = 5
        const val GIF = "image/gif"

        val CONFIGURATION = TimelineConfiguration(
            focus = TimelineFocus.Live(hideThreadedEvents = false),
            filter = TimelineFilter.OnlyMessage(listOf(RoomMessageEventMessageType.IMAGE)),
            internalIdPrefix = "photos",
            dateDividerMode = DateDividerMode.MONTHLY,
            trackReadReceipts = TimelineReadReceiptTracking.DISABLED,
            reportUtds = false
        )
    }
}
