package org.phalanx.android.core.contacts

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.phalanx.android.data.model.CustomEmote
import org.phalanx.android.data.model.Member
import org.phalanx.android.data.model.OutgoingMedia
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.data.repository.TimelineSession
import org.phalanx.android.data.repository.TimelineUnavailableException

/**
 * One person's or group's chats on every network they were linked on, as one conversation. A
 * message is acted on in the chat it came from, a reply goes where the quoted message is, and
 * anything new goes to [target]: the network the others last wrote from, or the one the user
 * picked, until the others write again.
 */
class PersonTimelineSession internal constructor(
    /** Room ID to its open timeline, in the person's order; the first keeps the draft. */
    private val chats: Map<String, TimelineSession>,
    override val title: String,
    /** False once a group is among the chats, so messages carry their sender's name. */
    override val isDirect: Boolean,
    scope: CoroutineScope
) : TimelineSession {
    val roomIds: List<String> = chats.keys.toList()

    private val parts: StateFlow<List<ChatPart>> = combine(
        chats.map { (roomId, chat) ->
            combine(chat.entries, chat.hasMoreHistory) { entries, more -> ChatPart(roomId, entries, more) }
        }
    ) { it.toList() }.stateIn(scope, SharingStarted.Eagerly, chats.map { (id, chat) -> chat.part(id) })

    private val merged: StateFlow<MergedTimeline> = parts.map { mergeTimelines(it) }
        .stateIn(scope, SharingStarted.Eagerly, mergeTimelines(parts.value))

    override val entries: StateFlow<List<TimelineEntry>> = merged.map { it.entries }
        .stateIn(scope, SharingStarted.Eagerly, merged.value.entries)

    override val hasMoreHistory: StateFlow<Boolean> = parts.map { all -> all.any { it.hasMore } }
        .stateIn(scope, SharingStarted.Eagerly, parts.value.any { it.hasMore })

    override val typing: StateFlow<List<Member>> = combine(chats.values.map { it.typing }) { lists ->
        lists.flatMap { it }.distinctBy { it.userId }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    // The user's pick, with when the others had last written then: a newer message from them, on
    // whichever network, takes the target back to where they are.
    private data class Choice(val roomId: String, val theirsAt: Long?)

    private val chosen = MutableStateFlow<Choice?>(null)

    /** Where a new message goes. */
    val target: StateFlow<String> = combine(parts, chosen) { all, pick -> targetOf(all, pick) }
        .stateIn(scope, SharingStarted.Eagerly, targetOf(parts.value, null))

    init {
        require(chats.size > 1) { "A person's thread joins two chats or more" }
        // A chat whose side is empty yet holds history would hide the whole thread; start loading it.
        scope.launch { if (loadedBackTo(parts.value) == null) loadOlder() }
    }

    fun choose(roomId: String) {
        require(roomId in chats) { "Not one of this person's chats" }
        chosen.value = Choice(roomId, lastTheirsMillis(parts.value))
    }

    private fun targetOf(all: List<ChatPart>, pick: Choice?): String {
        val kept = pick?.takeIf { (lastTheirsMillis(all) ?: Long.MIN_VALUE) <= (it.theirsAt ?: Long.MIN_VALUE) }
        return kept?.roomId ?: lastUsedChat(all) ?: roomIds.first()
    }

    /**
     * The chat a row of [entries] came from, and the row's ID there; null for a day divider or a
     * row no longer shown.
     */
    fun origin(entryId: String): Pair<String, String>? =
        merged.value.origins[entryId]?.let { it.roomId to it.message.id }

    private val targetChat: TimelineSession get() = checkNotNull(chats[target.value])

    override fun setTyping(typing: Boolean) = targetChat.setTyping(typing)

    override suspend fun send(text: String, mentions: List<Member>, replyTo: TimelineEntry.Message?): Result<Unit> {
        if (replyTo == null) return targetChat.send(text, mentions, null)
        return on(replyTo) { chat, original -> chat.send(text, mentions, original) }
    }

    override suspend fun sendMedia(media: OutgoingMedia, caption: String?, replyTo: TimelineEntry.Message?) =
        if (replyTo == null) {
            targetChat.sendMedia(media, caption, null)
        } else {
            on(replyTo) { chat, original -> chat.sendMedia(media, caption, original) }
        }

    override suspend fun edit(message: TimelineEntry.Message, text: String, mentions: List<Member>) =
        on(message) { chat, original -> chat.edit(original, text, mentions) }

    override suspend fun toggleReaction(message: TimelineEntry.Message, key: String) =
        on(message) { chat, original -> chat.toggleReaction(original, key) }

    override suspend fun redact(message: TimelineEntry.Message) =
        on(message) { chat, original -> chat.redact(original) }

    override suspend fun retrySend(message: TimelineEntry.Message) =
        on(message) { chat, original -> chat.retrySend(original) }

    override suspend fun setPinned(message: TimelineEntry.Message, pinned: Boolean) =
        on(message) { chat, original -> chat.setPinned(original, pinned) }

    override suspend fun loadDraft(): String? = chats.values.first().loadDraft()

    override fun saveDraft(text: String) = chats.values.first().saveDraft(text)

    /**
     * Loads the chat the thread stops at, again while that shows nothing new: its page can end
     * before another chat's oldest message, which keeps the cut where it was.
     */
    override suspend fun loadOlder(): Result<Unit> {
        // Read from the chats directly: the merged flows catch up a moment after a page lands.
        fun current() = chats.map { (roomId, chat) -> chat.part(roomId) }
        val before = mergeTimelines(current()).entries.size
        var result = Result.success(Unit)
        var loads = 0
        var lagging = laggingChat(current())
        while (lagging != null && result.isSuccess && loads++ < MAX_LOADS) {
            result = checkNotNull(chats[lagging]).loadOlder()
            lagging = laggingChat(current()).takeIf { mergeTimelines(current()).entries.size == before }
        }
        return result
    }

    override suspend fun joinedMembers(): List<Member> = targetChat.joinedMembers()

    override suspend fun emotes(): List<CustomEmote> = targetChat.emotes()

    override suspend fun members(userIds: List<String>): List<Member> = targetChat.members(userIds)

    /** Every chat is read once the thread is: each keeps its own unread count. */
    override suspend fun markRead(): Result<Unit> = coroutineScope {
        chats.values.map { async { it.markRead() } }.awaitAll().firstOrNull { it.isFailure } ?: Result.success(Unit)
    }

    private suspend fun on(
        message: TimelineEntry.Message,
        block: suspend (TimelineSession, TimelineEntry.Message) -> Result<Unit>
    ): Result<Unit> {
        val origin = merged.value.origins[message.id] ?: return Result.failure(TimelineUnavailableException())
        return block(checkNotNull(chats[origin.roomId]), origin.message)
    }

    private fun TimelineSession.part(roomId: String) = ChatPart(roomId, entries.value, hasMoreHistory.value)

    private companion object {
        const val MAX_LOADS = 5
    }
}
