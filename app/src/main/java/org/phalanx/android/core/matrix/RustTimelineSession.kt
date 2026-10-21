package org.phalanx.android.core.matrix

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.matrix.rustcomponents.sdk.ClientException
import org.matrix.rustcomponents.sdk.ReceiptType
import org.matrix.rustcomponents.sdk.Room
import org.matrix.rustcomponents.sdk.TaskHandle
import org.matrix.rustcomponents.sdk.Timeline
import org.matrix.rustcomponents.sdk.TimelineDiff
import org.matrix.rustcomponents.sdk.TimelineListener
import org.matrix.rustcomponents.sdk.messageEventContentFromMarkdown
import org.phalanx.android.core.prefs.SettingsStore
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.data.repository.TimelineSession

internal class RustTimelineSession private constructor(
    private val room: Room,
    private val timeline: Timeline,
    private val settings: SettingsStore,
    override val title: String,
    override val isDirect: Boolean
) : TimelineSession {
    private val lock = Any()

    // Mirrors the SDK list index for index, including the items that are not shown (null), because
    // diffs address positions in the SDK's list.
    private val items = ArrayList<TimelineEntry?>()
    private var listenerHandle: TaskHandle? = null
    private val paginating = Mutex()

    private val mutableEntries = MutableStateFlow<List<TimelineEntry>>(emptyList())
    override val entries: StateFlow<List<TimelineEntry>> = mutableEntries

    private val mutableHasMore = MutableStateFlow(true)
    override val hasMoreHistory: StateFlow<Boolean> = mutableHasMore

    override suspend fun send(text: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            timeline.send(messageEventContentFromMarkdown(text)).close()
            Result.success(Unit)
        } catch (e: ClientException) {
            Result.failure(e)
        }
    }

    override suspend fun loadOlder(): Result<Unit> {
        if (!mutableHasMore.value || !paginating.tryLock()) return Result.success(Unit)
        return try {
            withContext(Dispatchers.IO) {
                if (timeline.paginateBackwards(PAGE_SIZE.toUShort())) mutableHasMore.value = false
            }
            Result.success(Unit)
        } catch (e: ClientException) {
            Result.failure(e)
        } finally {
            paginating.unlock()
        }
    }

    // Private receipts by default: the room still clears its unread count, but other members are
    // not told when this account read their messages unless the user turned that on.
    override suspend fun markRead(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val type = if (settings.current().publicReadReceipts) ReceiptType.READ else ReceiptType.READ_PRIVATE
            timeline.markAsRead(type)
            Result.success(Unit)
        } catch (e: ClientException) {
            Result.failure(e)
        }
    }

    /** Called by the repository only, when the room leaves its cache. */
    fun close() {
        listenerHandle?.let {
            it.cancel()
            it.destroy()
        }
        timeline.destroy()
        room.destroy()
    }

    private suspend fun start() {
        listenerHandle = timeline.addListener(
            object : TimelineListener {
                override fun onUpdate(diff: List<TimelineDiff>) = apply(diff)
            }
        )
    }

    private fun apply(diffs: List<TimelineDiff>) {
        synchronized(lock) {
            for (diff in diffs) {
                when (diff) {
                    is TimelineDiff.Append -> diff.values.forEach { items.add(it.toEntry()) }
                    is TimelineDiff.PushBack -> items.add(diff.value.toEntry())
                    is TimelineDiff.PushFront -> items.add(0, diff.value.toEntry())
                    is TimelineDiff.Insert -> items.add(diff.index.toInt(), diff.value.toEntry())
                    is TimelineDiff.Set -> items[diff.index.toInt()] = diff.value.toEntry()
                    is TimelineDiff.Remove -> items.removeAt(diff.index.toInt())
                    is TimelineDiff.Reset -> {
                        items.clear()
                        diff.values.forEach { items.add(it.toEntry()) }
                    }
                    is TimelineDiff.Truncate -> items.subList(diff.length.toInt(), items.size).clear()
                    TimelineDiff.PopBack -> items.removeLastOrNull()
                    TimelineDiff.PopFront -> items.removeFirstOrNull()
                    TimelineDiff.Clear -> items.clear()
                }
            }
            mutableEntries.value = items.filterNotNull()
        }
    }

    companion object {
        private const val PAGE_SIZE = 30

        /** Opens [room]'s live timeline; the session owns [room] from here and releases it in [close]. */
        suspend fun open(room: Room, settings: SettingsStore): RustTimelineSession {
            val timeline = room.timeline()
            val session = RustTimelineSession(room, timeline, settings, room.displayName() ?: room.id(), room.isDirect())
            session.start()
            return session
        }
    }
}
