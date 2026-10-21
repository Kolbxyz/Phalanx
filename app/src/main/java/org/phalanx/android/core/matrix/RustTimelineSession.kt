package org.phalanx.android.core.matrix

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.matrix.rustcomponents.sdk.ClientException
import org.matrix.rustcomponents.sdk.EventOrTransactionId
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Mirrors the SDK list index for index, including the items that are not shown (null), because
    // diffs address positions in the SDK's list.
    private val items = ArrayList<TimelineEntry?>()
    private var listenerHandle: TaskHandle? = null
    private val paginating = Mutex()

    private val mutableEntries = MutableStateFlow<List<TimelineEntry>>(emptyList())
    override val entries: StateFlow<List<TimelineEntry>> = mutableEntries

    private val mutableHasMore = MutableStateFlow(true)
    override val hasMoreHistory: StateFlow<Boolean> = mutableHasMore

    // Replies whose original the SDK had not loaded; asked for once each, the item updates itself.
    private val requestedReplies = HashSet<String>()
    private val context = MappingContext(room.ownUserId()) { eventId ->
        if (requestedReplies.add(eventId)) scope.launch { runCatchingSdk { timeline.fetchDetailsForEvent(eventId) } }
    }

    override suspend fun send(text: String, replyTo: TimelineEntry.Message?): Result<Unit> = io {
        val content = messageEventContentFromMarkdown(text)
        val eventId = replyTo?.eventId
        if (eventId != null) timeline.sendReply(content, eventId).close() else timeline.send(content).close()
    }

    override suspend fun toggleReaction(message: TimelineEntry.Message, key: String): Result<Unit> = io {
        val target = message.target() ?: return@io
        timeline.toggleReaction(target, key)
    }

    override suspend fun redact(message: TimelineEntry.Message): Result<Unit> = io {
        val target = message.target() ?: return@io
        timeline.redactEvent(target, reason = null)
    }

    private fun TimelineEntry.Message.target(): EventOrTransactionId? =
        eventId?.let { EventOrTransactionId.EventId(it) }
            ?: transactionId?.let { EventOrTransactionId.TransactionId(it) }

    private suspend fun io(block: suspend () -> Unit): Result<Unit> =
        withContext(Dispatchers.IO) { runCatchingSdk(block) }

    private suspend fun runCatchingSdk(block: suspend () -> Unit): Result<Unit> = try {
        block()
        Result.success(Unit)
    } catch (e: ClientException) {
        Result.failure(e)
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
        scope.cancel()
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
                    is TimelineDiff.Append -> diff.values.forEach { items.add(it.toEntry(context)) }
                    is TimelineDiff.PushBack -> items.add(diff.value.toEntry(context))
                    is TimelineDiff.PushFront -> items.add(0, diff.value.toEntry(context))
                    is TimelineDiff.Insert -> items.add(diff.index.toInt(), diff.value.toEntry(context))
                    is TimelineDiff.Set -> items[diff.index.toInt()] = diff.value.toEntry(context)
                    is TimelineDiff.Remove -> items.removeAt(diff.index.toInt())
                    is TimelineDiff.Reset -> {
                        items.clear()
                        diff.values.forEach { items.add(it.toEntry(context)) }
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
