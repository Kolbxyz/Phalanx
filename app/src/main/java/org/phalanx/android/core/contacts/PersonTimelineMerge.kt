package org.phalanx.android.core.contacts

import java.time.Instant
import java.time.ZoneId
import org.phalanx.android.data.model.TimelineEntry

/** One chat's side of a person's thread: what it has loaded, and whether it holds older messages. */
internal data class ChatPart(val roomId: String, val entries: List<TimelineEntry>, val hasMore: Boolean)

/** A person's thread: its rows, oldest first, and the chat each message row came from, by row ID. */
internal data class MergedTimeline(
    val entries: List<TimelineEntry>,
    /** Row ID to the chat and the message as that chat knows it, to act on it there. */
    val origins: Map<String, Origin>
) {
    data class Origin(val roomId: String, val message: TimelineEntry.Message)
}

/**
 * The person's chats as one thread, messages by time with day dividers rebuilt across them.
 * Only what every chat has loaded back to is shown: a chat with older messages still to load would
 * otherwise leave a gap the others' messages fill, which reads as a conversation that never was.
 * Row IDs are prefixed with the room, since each chat numbers its rows on its own.
 */
internal fun mergeTimelines(parts: List<ChatPart>, zone: ZoneId = ZoneId.systemDefault()): MergedTimeline {
    val cutoff = loadedBackTo(parts) ?: return MergedTimeline(emptyList(), emptyMap())
    val messages = parts.flatMap { part ->
        part.entries.filterIsInstance<TimelineEntry.Message>()
            .filter { it.timestampMillis >= cutoff }
            .map { Origin(part.roomId, it) }
    }.sortedBy { it.message.timestampMillis }
    val entries = ArrayList<TimelineEntry>()
    val origins = HashMap<String, MergedTimeline.Origin>()
    var lastDay: Long? = null
    for ((roomId, message) in messages) {
        val day = Instant.ofEpochMilli(message.timestampMillis).atZone(zone).toLocalDate().toEpochDay()
        if (day != lastDay) {
            entries.add(TimelineEntry.DayDivider("day-$day", message.timestampMillis))
            lastDay = day
        }
        val id = "$roomId/${message.id}"
        entries.add(message.copy(id = id))
        origins[id] = MergedTimeline.Origin(roomId, message)
    }
    return MergedTimeline(entries, origins)
}

/**
 * The time every chat is loaded back to: the newest of the oldest messages among chats with more
 * to load. Long.MIN_VALUE when all are loaded to their start; null when a chat with history to load
 * has no message loaded yet, so nothing can be placed safely.
 */
internal fun loadedBackTo(parts: List<ChatPart>): Long? {
    var cutoff = Long.MIN_VALUE
    for (part in parts.filter { it.hasMore }) {
        val oldest = part.oldestMillis() ?: return null
        cutoff = maxOf(cutoff, oldest)
    }
    return cutoff
}

/**
 * The chat to load older messages from: the one the shown thread stops at, which is the chat with
 * more to load whose oldest loaded message is newest, or one with none loaded at all. Null when
 * every chat is loaded to its start.
 */
internal fun laggingChat(parts: List<ChatPart>): String? = parts.filter { it.hasMore }
    .maxByOrNull { it.oldestMillis() ?: Long.MAX_VALUE }?.roomId

/** When anyone but the user last wrote, in any of the chats; null before anyone has. */
internal fun lastTheirsMillis(parts: List<ChatPart>): Long? = parts.flatMap { it.entries }
    .filterIsInstance<TimelineEntry.Message>()
    .filter { !it.isOwn }
    .maxOfOrNull { it.timestampMillis }

/** The chat the others last wrote from, else the one with the latest message; null with no message. */
internal fun lastUsedChat(parts: List<ChatPart>): String? {
    val messages = parts.flatMap { part ->
        part.entries.filterIsInstance<TimelineEntry.Message>().map { part.roomId to it }
    }
    val theirs = messages.filter { !it.second.isOwn }.maxByOrNull { it.second.timestampMillis }
    return (theirs ?: messages.maxByOrNull { it.second.timestampMillis })?.first
}

private typealias Origin = MergedTimeline.Origin

private fun ChatPart.oldestMillis(): Long? =
    entries.firstNotNullOfOrNull { (it as? TimelineEntry.Message)?.timestampMillis }
