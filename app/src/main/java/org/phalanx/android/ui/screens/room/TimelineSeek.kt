package org.phalanx.android.ui.screens.room

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.data.repository.TimelineSession

// Pages of about 30 events: far enough back for a pinned message or a photo from last year.
private const val MAX_SEEK_PAGES = 60
private const val SEEK_WAIT_MILLIS = 10_000L

/**
 * Pages back through history until [eventId] is loaded. Returns its entry ID, or null when the
 * start of the room, a failed page or the page limit came first.
 */
internal suspend fun TimelineSession.seek(eventId: String): String? {
    for (page in 0..MAX_SEEK_PAGES) {
        entries.value.firstOrNull { (it as? TimelineEntry.Message)?.eventId == eventId }?.let { return it.id }
        if (page == MAX_SEEK_PAGES || !hasMoreHistory.value) break
        val before = entries.value.size
        if (loadOlder().isFailure) break
        // The list may be paging already, in which case this call returned at once: wait for that page.
        if (entries.value.size == before) {
            withTimeoutOrNull(SEEK_WAIT_MILLIS) { entries.first { it.size != before } } ?: break
        }
    }
    return null
}
