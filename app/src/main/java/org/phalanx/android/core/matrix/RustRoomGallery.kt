package org.phalanx.android.core.matrix

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.matrix.rustcomponents.sdk.ClientException
import org.matrix.rustcomponents.sdk.DateDividerMode
import org.matrix.rustcomponents.sdk.Room
import org.matrix.rustcomponents.sdk.RoomMessageEventMessageType
import org.matrix.rustcomponents.sdk.TaskHandle
import org.matrix.rustcomponents.sdk.Timeline
import org.matrix.rustcomponents.sdk.TimelineConfiguration
import org.matrix.rustcomponents.sdk.TimelineDiff
import org.matrix.rustcomponents.sdk.TimelineFilter
import org.matrix.rustcomponents.sdk.TimelineFocus
import org.matrix.rustcomponents.sdk.TimelineListener
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.data.repository.RoomGallery
import uniffi.matrix_sdk_ui.TimelineReadReceiptTracking

/** Two extra timelines of one room: its pinned events, and its messages filtered down to media. */
internal class RustRoomGallery private constructor(
    private val room: Room,
    private val pinnedTimeline: Timeline,
    private val mediaTimeline: Timeline
) : RoomGallery {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val context = MappingContext(room.ownUserId()) { }
    private val pinnedItems = TimelineItems(context)
    private val mediaItems = TimelineItems(context)
    private val handles = ArrayList<TaskHandle>()
    private val paginating = Mutex()

    override val pinned: StateFlow<List<TimelineEntry.Message>> = pinnedItems.entries
        .map { entries -> entries.filterIsInstance<TimelineEntry.Message>() }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override val media: StateFlow<List<TimelineEntry.Message>> = mediaItems.entries
        .map { entries -> entries.filterIsInstance<TimelineEntry.Message>().asReversed() }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val mutableHasMore = MutableStateFlow(true)
    override val hasMoreMedia: StateFlow<Boolean> = mutableHasMore

    override suspend fun loadMoreMedia() {
        if (!mutableHasMore.value || !paginating.tryLock()) return
        try {
            withContext(Dispatchers.IO) {
                if (mediaTimeline.paginateBackwards(PAGE_SIZE.toUShort())) mutableHasMore.value = false
            }
        } catch (_: ClientException) {
            // Offline or a server hiccup: the grid asks again when scrolled to its end.
        } finally {
            paginating.unlock()
        }
    }

    private suspend fun start() {
        handles += pinnedTimeline.addListener(listener(pinnedItems))
        handles += mediaTimeline.addListener(listener(mediaItems))
    }

    private fun listener(items: TimelineItems) = object : TimelineListener {
        override fun onUpdate(diff: List<TimelineDiff>) = items.apply(diff)
    }

    fun close() {
        scope.cancel()
        handles.forEach {
            it.cancel()
            it.destroy()
        }
        pinnedTimeline.destroy()
        mediaTimeline.destroy()
        room.destroy()
    }

    companion object {
        private const val PAGE_SIZE = 40

        private val MEDIA_TYPES = listOf(
            RoomMessageEventMessageType.IMAGE,
            RoomMessageEventMessageType.VIDEO,
            RoomMessageEventMessageType.FILE,
            RoomMessageEventMessageType.AUDIO
        )

        /** Takes ownership of [room] and releases it in [close]. */
        suspend fun open(room: Room): RustRoomGallery {
            val pinned = room.timelineWithConfiguration(
                configuration(TimelineFocus.PinnedEvents, TimelineFilter.All, prefix = "pinned")
            )
            val media = room.timelineWithConfiguration(
                configuration(
                    TimelineFocus.Live(hideThreadedEvents = false),
                    TimelineFilter.OnlyMessage(MEDIA_TYPES),
                    prefix = "media"
                )
            )
            return RustRoomGallery(room, pinned, media).apply {
                start()
                loadMoreMedia()
            }
        }

        // Read receipts and decryption-failure reports belong to the live timeline only.
        private fun configuration(focus: TimelineFocus, filter: TimelineFilter, prefix: String) = TimelineConfiguration(
            focus = focus,
            filter = filter,
            internalIdPrefix = prefix,
            dateDividerMode = DateDividerMode.MONTHLY,
            trackReadReceipts = TimelineReadReceiptTracking.DISABLED,
            reportUtds = false
        )
    }
}
