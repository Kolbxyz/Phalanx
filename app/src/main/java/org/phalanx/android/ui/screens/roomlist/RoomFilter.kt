package org.phalanx.android.ui.screens.roomlist

import androidx.annotation.StringRes
import org.phalanx.android.R
import org.phalanx.android.data.model.RoomSummary

/** Rooms quiet this long file themselves under Archived until something new arrives. */
internal const val AUTO_ARCHIVE_MILLIS = 30L * 24 * 60 * 60 * 1000

/** The chips above the list. Every filter but [Archived] leaves archived rooms out. */
enum class RoomFilter(@StringRes val label: Int) {
    All(R.string.roomlist_filter_all),
    Unread(R.string.roomlist_filter_unread),
    Favourites(R.string.roomlist_filter_favourites),
    People(R.string.roomlist_filter_people),
    Groups(R.string.roomlist_filter_groups),
    Archived(R.string.roomlist_filter_archived);

    fun matches(room: RoomSummary, nowMillis: Long): Boolean {
        val archived = room.isArchived(nowMillis)
        return when (this) {
            All -> !archived
            Unread -> !archived && room.hasUnread
            // Favourites are what the user asked to keep at hand, so they never auto-archive.
            Favourites -> room.isFavourite
            People -> !archived && room.isDirect
            Groups -> !archived && !room.isDirect
            Archived -> archived
        }
    }
}

/**
 * Archived by hand, or quiet for [AUTO_ARCHIVE_MILLIS] with nothing unread. Favourites and rooms
 * with unread messages stay in view.
 */
internal fun RoomSummary.isArchived(nowMillis: Long): Boolean {
    if (isFavourite) return false
    if (isLowPriority) return true
    val last = lastActivityMillis ?: return false
    return !hasUnread && nowMillis - last > AUTO_ARCHIVE_MILLIS
}
