package org.phalanx.android.ui.screens.room

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.data.repository.TimelineSession

/**
 * Where the user had stopped reading a room, or a person's thread, when it was opened. Built as
 * the screen opens, so [readUpTo]'s receipts and [unreadCount] are taken before reading moves them.
 */
internal class ReadAtOpen(
    scope: CoroutineScope,
    readUpTo: suspend () -> Set<String>,
    private val unreadCount: Long
) {
    // An unknown receipt leaves the unread count to place the first unread message.
    private val receipts = scope.async { readUpTo() }

    // Found once: later messages arrive after it, and reading the room must not move it.
    private var firstUnreadId: String? = null

    /**
     * The messages unread at open and every one since, oldest first, at most [max]; empty when
     * nothing was unread. Pages back through history to reach the first of them.
     */
    suspend fun unread(session: TimelineSession, max: Int): List<TimelineEntry.Message> {
        val anchor = firstUnreadId ?: find(session)?.also { firstUnreadId = it }
        return if (anchor == null) emptyList() else messagesOf(session).dropWhile { it.id != anchor }.takeLast(max)
    }

    /** The event ID of the first message unread at open, or null when there was none. */
    suspend fun firstUnreadEventId(session: TimelineSession): String? = unread(session, Int.MAX_VALUE)
        .firstOrNull()?.eventId

    private suspend fun find(session: TimelineSession): String? =
        if (unreadCount <= 0) null else afterReceipt(session) ?: byCount(session)

    // The first shown message after the newest of the receipts; the read event itself may be one
    // that is not shown. Null when no receipt is in reach of the history.
    private suspend fun afterReceipt(session: TimelineSession): String? {
        val readId = receipts.await().takeIf { it.isNotEmpty() }?.let { session.seekAny(it) } ?: return null
        val entries = session.entries.value
        val after = entries.drop(entries.indexOfFirst { it.id == readId } + 1).map { it.id }.toSet()
        return messagesOf(session).firstOrNull { it.id in after }?.id
    }

    // Without a receipt in the loaded history, the count says how far back the unread ones go.
    private suspend fun byCount(session: TimelineSession): String? {
        var pages = 0
        while (messagesOf(session).size < unreadCount && session.hasMoreHistory.value && pages++ < MAX_PAGES) {
            if (session.loadOlder().isFailure) break
        }
        val messages = messagesOf(session)
        return messages.getOrNull((messages.size - unreadCount).coerceAtLeast(0L).toInt())?.id
    }

    private companion object {
        const val MAX_PAGES = 20
    }
}

/** The messages a reader would see: redacted and undecryptable ones say nothing. */
internal fun messagesOf(session: TimelineSession): List<TimelineEntry.Message> =
    session.entries.value.filterIsInstance<TimelineEntry.Message>().filter {
        it.body != MessageBody.Redacted && it.body != MessageBody.Undecryptable
    }
