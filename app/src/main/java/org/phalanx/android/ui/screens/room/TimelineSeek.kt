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
internal suspend fun TimelineSession.seek(eventId: String): String? = seekAny(setOf(eventId))

/**
 * Pages back until one of [eventIds] is loaded and returns the entry ID of the newest one loaded,
 * or null as [seek] does. Paging goes newest to oldest, so the first one found is the newest.
 */
internal suspend fun TimelineSession.seekAny(eventIds: Set<String>): String? {
    var found = newestOf(eventIds)
    var pages = 0
    while (found == null && pages++ < MAX_SEEK_PAGES && loadPage()) {
        found = newestOf(eventIds)
    }
    return found
}

private fun TimelineSession.newestOf(eventIds: Set<String>): String? = entries.value
    .lastOrNull { entry -> (entry as? TimelineEntry.Message)?.eventId?.let { it in eventIds } == true }?.id

/** One more page of history; false at the start of the room, or when the page failed or never came. */
private suspend fun TimelineSession.loadPage(): Boolean {
    val before = entries.value.size
    if (!hasMoreHistory.value || loadOlder().isFailure) return false
    // The list may be paging already, in which case this call returned at once: wait for that page.
    return entries.value.size != before ||
        withTimeoutOrNull(SEEK_WAIT_MILLIS) { entries.first { it.size != before } } != null
}
