package org.phalanx.android.core.matrix

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.matrix.rustcomponents.sdk.ClientException
import org.matrix.rustcomponents.sdk.ComposerDraft
import org.matrix.rustcomponents.sdk.ComposerDraftType
import org.matrix.rustcomponents.sdk.EditedContent
import org.matrix.rustcomponents.sdk.EmoteMessageContent
import org.matrix.rustcomponents.sdk.EventOrTransactionId
import org.matrix.rustcomponents.sdk.MessageType
import org.matrix.rustcomponents.sdk.ReceiptType
import org.matrix.rustcomponents.sdk.Room
import org.matrix.rustcomponents.sdk.RoomException
import org.matrix.rustcomponents.sdk.RoomMessageEventContentWithoutRelation
import org.matrix.rustcomponents.sdk.TaskHandle
import org.matrix.rustcomponents.sdk.TextMessageContent
import org.matrix.rustcomponents.sdk.Timeline
import org.matrix.rustcomponents.sdk.TimelineDiff
import org.matrix.rustcomponents.sdk.TimelineListener
import org.matrix.rustcomponents.sdk.messageEventContentFromMarkdown
import org.matrix.rustcomponents.sdk.messageEventContentFromMarkdownAsEmote
import org.matrix.rustcomponents.sdk.messageEventContentNew
import org.matrix.rustcomponents.sdk.use
import org.phalanx.android.core.media.AttachmentPreparer
import org.phalanx.android.core.prefs.SettingsStore
import org.phalanx.android.data.model.OutgoingMedia
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.data.repository.TimelineSession
import java.util.concurrent.ConcurrentHashMap

internal class RustTimelineSession private constructor(
    private val room: Room,
    private val timeline: Timeline,
    private val settings: SettingsStore,
    private val attachments: AttachmentPreparer,
    override val title: String,
    override val isDirect: Boolean
) : TimelineSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var listenerHandle: TaskHandle? = null
    private val paginating = Mutex()

    private val typingState = RoomTyping(room, room.ownUserId(), scope)
    override val typing: StateFlow<List<String>> = typingState.names

    private val mutableHasMore = MutableStateFlow(true)
    override val hasMoreHistory: StateFlow<Boolean> = mutableHasMore

    // Replies whose original the SDK had not loaded; asked for once each, the item updates itself.
    private val requestedReplies: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val context = MappingContext(room.ownUserId()) { eventId ->
        if (requestedReplies.add(eventId)) {
            scope.launch {
                // Forgotten on failure (offline, say), so the next update of the reply asks again.
                runCatchingSdk { timeline.fetchDetailsForEvent(eventId) }
                    .onFailure { requestedReplies.remove(eventId) }
            }
        }
    }

    private val items = TimelineItems(context)
    override val entries: StateFlow<List<TimelineEntry>> = items.entries

    override suspend fun send(text: String, replyTo: TimelineEntry.Message?): Result<Unit> = io {
        val content = contentOf(text)
        val eventId = replyTo?.eventId
        if (eventId != null) timeline.sendReply(content, eventId).close() else timeline.send(content).close()
    }

    // On the session's scope, so leaving the room doesn't stop the upload or leak its files.
    override suspend fun sendMedia(media: OutgoingMedia, caption: String?, replyTo: TimelineEntry.Message?) =
        scope.async {
            try {
                runCatchingSdk { timeline.upload(media, caption, replyTo?.eventId).use { it.join() } }
            } finally {
                attachments.discard(media)
            }
        }.await()

    override suspend fun edit(message: TimelineEntry.Message, text: String): Result<Unit> = io {
        val target = message.target() ?: return@io
        timeline.edit(target, EditedContent.RoomMessage(contentOf(text)))
    }

    /** "/me waves" is an emote, as in every Matrix client; markdown follows the user's setting. */
    private suspend fun contentOf(text: String): RoomMessageEventContentWithoutRelation {
        val emote = text.startsWith(EMOTE_PREFIX)
        val body = if (emote) text.removePrefix(EMOTE_PREFIX) else text
        return when {
            !settings.current().markdown -> messageEventContentNew(
                if (emote) {
                    MessageType.Emote(EmoteMessageContent(body, null))
                } else {
                    MessageType.Text(TextMessageContent(body, null))
                }
            )
            emote -> messageEventContentFromMarkdownAsEmote(body)
            else -> messageEventContentFromMarkdown(body)
        }
    }

    override suspend fun loadDraft(): String? = withContext(Dispatchers.IO) {
        try {
            room.loadComposerDraft(threadRoot = null)?.use { it.plainText }?.takeIf { it.isNotBlank() }
        } catch (_: ClientException) {
            null
        }
    }

    override fun saveDraft(text: String) {
        scope.launch {
            runCatchingSdk {
                if (text.isBlank()) {
                    room.clearComposerDraft(threadRoot = null)
                } else {
                    val draft = ComposerDraft(text, null, ComposerDraftType.NewMessage, emptyList())
                    draft.use { room.saveComposerDraft(it, threadRoot = null) }
                }
            }
        }
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
    } catch (e: RoomException) {
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

    override fun setTyping(typing: Boolean) = typingState.notice(typing)

    /** Called by the repository only, when the room leaves its cache. */
    fun close() {
        typingState.close()
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
                override fun onUpdate(diff: List<TimelineDiff>) = items.apply(diff)
            }
        )
    }

    companion object {
        private const val PAGE_SIZE = 30
        private const val EMOTE_PREFIX = "/me "

        /** Opens [room]'s live timeline; the session owns [room] from here and releases it in [close]. */
        suspend fun open(room: Room, settings: SettingsStore, attachments: AttachmentPreparer): RustTimelineSession {
            val timeline = room.timeline()
            val title = room.displayName() ?: room.id()
            val session = RustTimelineSession(room, timeline, settings, attachments, title, room.isDirect())
            session.start()
            return session
        }
    }
}
