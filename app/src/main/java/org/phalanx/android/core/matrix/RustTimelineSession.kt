package org.phalanx.android.core.matrix

import java.util.concurrent.ConcurrentHashMap
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
import org.matrix.rustcomponents.sdk.FormattedBody
import org.matrix.rustcomponents.sdk.Mentions
import org.matrix.rustcomponents.sdk.MessageFormat
import org.matrix.rustcomponents.sdk.MessageType
import org.matrix.rustcomponents.sdk.NoticeMessageContent
import org.matrix.rustcomponents.sdk.ReceiptType
import org.matrix.rustcomponents.sdk.Room
import org.matrix.rustcomponents.sdk.RoomException
import org.matrix.rustcomponents.sdk.RoomMessageEventContentWithoutRelation
import org.matrix.rustcomponents.sdk.TaskHandle
import org.matrix.rustcomponents.sdk.TextMessageContent
import org.matrix.rustcomponents.sdk.Timeline
import org.matrix.rustcomponents.sdk.TimelineDiff
import org.matrix.rustcomponents.sdk.TimelineListener
import org.matrix.rustcomponents.sdk.messageEventContentNew
import org.matrix.rustcomponents.sdk.use
import org.phalanx.android.core.format.OutgoingMessage
import org.phalanx.android.core.media.AttachmentPreparer
import org.phalanx.android.core.prefs.SettingsStore
import org.phalanx.android.data.model.CustomEmote
import org.phalanx.android.data.model.Member
import org.phalanx.android.data.model.OutgoingMedia
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.data.repository.NotForwardableException
import org.phalanx.android.data.repository.TimelineSession

internal class RustTimelineSession private constructor(
    private val room: Room,
    private val timeline: Timeline,
    private val settings: SettingsStore,
    private val attachments: AttachmentPreparer,
    private val drafts: RustDraftsRepository,
    private val emotes: SessionEmotes,
    override val title: String,
    override val isDirect: Boolean
) : TimelineSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var listenerHandle: TaskHandle? = null
    private val paginating = Mutex()

    private val typingState = RoomTyping(room, room.ownUserId(), scope)
    override val typing: StateFlow<List<Member>> = typingState.members

    private val mutableHasMore = MutableStateFlow(true)
    override val hasMoreHistory: StateFlow<Boolean> = mutableHasMore

    // Replies whose quoted event the SDK had not loaded; asked for once each, the item updates itself.
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

    override suspend fun send(text: String, mentions: List<Member>, replyTo: TimelineEntry.Message?): Result<Unit> =
        io {
            val content = contentOf(text, mentions)
            val eventId = replyTo?.eventId
            if (eventId != null) timeline.sendReply(content, eventId).close() else timeline.send(content).close()
        }

    // On the session's scope, so leaving the room doesn't stop the upload or leak its files.
    override suspend fun sendMedia(media: OutgoingMedia, caption: String?, replyTo: TimelineEntry.Message?) =
        scope.async {
            try {
                val html = caption?.let {
                    OutgoingMessage.of(it, emptyList(), settings.current().markdown, emotes.byShortcode()).html
                }
                runCatchingSdk { timeline.upload(media, caption, html, replyTo?.eventId).use { it.join() } }
            } finally {
                attachments.discard(media)
            }
        }.await()

    override suspend fun edit(message: TimelineEntry.Message, text: String, mentions: List<Member>): Result<Unit> =
        io {
            val target = message.target() ?: return@io
            timeline.edit(target, EditedContent.RoomMessage(contentOf(text, mentions)))
        }

    /** Markdown follows the user's setting; mentions are rendered and listed either way. */
    private suspend fun contentOf(text: String, mentions: List<Member>): RoomMessageEventContentWithoutRelation {
        val message = OutgoingMessage.of(text, mentions, settings.current().markdown, emotes.byShortcode())
        val formatted = message.html?.let { FormattedBody(MessageFormat.Html, it) }
        val type = when (message.kind) {
            OutgoingMessage.Kind.Text -> MessageType.Text(TextMessageContent(message.body, formatted))
            OutgoingMessage.Kind.Emote -> MessageType.Emote(EmoteMessageContent(message.body, formatted))
            OutgoingMessage.Kind.Notice -> MessageType.Notice(NoticeMessageContent(message.body, formatted))
        }
        return messageEventContentNew(type).use { it.withMentions(Mentions(message.userIds, message.mentionsRoom)) }
    }

    override suspend fun loadDraft(): String? = withContext(Dispatchers.IO) {
        try {
            room.loadComposerDraft(threadRoot = null)?.use { it.plainText }?.takeIf { it.isNotBlank() }
        } catch (_: ClientException) {
            null
        }
    }

    override fun saveDraft(text: String) {
        drafts.saved(room.id(), text)
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

    override suspend fun setPinned(message: TimelineEntry.Message, pinned: Boolean): Result<Unit> = io {
        val eventId = message.eventId ?: return@io
        if (pinned) timeline.pinEvent(eventId) else timeline.unpinEvent(eventId)
    }

    private fun TimelineEntry.Message.target(): EventOrTransactionId? =
        eventId?.let { EventOrTransactionId.EventId(it) }
            ?: transactionId?.let { EventOrTransactionId.TransactionId(it) }

    private suspend fun io(block: suspend () -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            runCatchingSdk(block)
        } catch (e: NotForwardableException) {
            Result.failure(e)
        }
    }

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
                // A page can be nothing but hidden events (a new room's creation and joins), which
                // would leave the list unchanged and its spinner up; keep going until something shows.
                val before = items.entries.value.size
                var pages = 0
                while (items.entries.value.size == before && pages < MAX_PAGES_PER_LOAD) {
                    pages++
                    if (timeline.paginateBackwards(PAGE_SIZE.toUShort())) {
                        mutableHasMore.value = false
                        break
                    }
                }
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

    /** Sends [eventId]'s content again through [to]'s timeline, as Element X does. */
    suspend fun forward(eventId: String, to: RustTimelineSession): Result<Unit> = io {
        val content = timeline.forwardableContent(eventId) ?: throw NotForwardableException()
        to.timeline.send(content).close()
    }

    override suspend fun members(userIds: List<String>): List<Member> = withContext(Dispatchers.IO) {
        userIds.map { userId ->
            try {
                val member = room.member(userId)
                Member(userId, member.displayName?.takeIf { it.isNotBlank() } ?: userId, mediaRefOf(member.avatarUrl))
            } catch (_: ClientException) {
                // Left the room or never loaded: the ID is still who they are.
                Member(userId, userId, null)
            }
        }
    }

    override suspend fun emotes(): List<CustomEmote> = withContext(Dispatchers.IO) { emotes.all() }

    override suspend fun joinedMembers(): List<Member> = withContext(Dispatchers.IO) {
        try {
            // From the server when it can, so a room synced with lazy-loaded members is complete.
            val iterator = try {
                room.members()
            } catch (_: ClientException) {
                room.membersNoSync()
            }
            val own = room.ownUserId()
            iterator.use { it.participants() }.filter { !it.invited && it.member.userId != own }.map { it.member }
        } catch (_: ClientException) {
            emptyList()
        }
    }

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
        // Rooms are synced with lazy-loaded members, so senders not yet seen have no profile and
        // would show as their Matrix ID (a bridge's "@whatsapp_lid-…"); their items update once loaded.
        scope.launch { runCatchingSdk { timeline.fetchMembers() } }
        scope.launch { entries.collect(emotes::learn) }
    }

    companion object {
        private const val PAGE_SIZE = 30
        private const val MAX_PAGES_PER_LOAD = 5

        /** Opens [room]'s live timeline; the session owns [room] from here and releases it in [close]. */
        suspend fun open(
            room: Room,
            settings: SettingsStore,
            attachments: AttachmentPreparer,
            drafts: RustDraftsRepository,
            roomEmotes: RoomEmotes
        ): RustTimelineSession {
            val timeline = room.timeline()
            val title = room.displayName() ?: room.id()
            val emotes = roomEmotes.forRoom(room.ownUserId(), room.id(), room.roomInfo().bridge())
            val session =
                RustTimelineSession(room, timeline, settings, attachments, drafts, emotes, title, room.isDirect())
            session.start()
            return session
        }
    }
}
