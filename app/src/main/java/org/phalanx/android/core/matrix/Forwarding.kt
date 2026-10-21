package org.phalanx.android.core.matrix

import org.matrix.rustcomponents.sdk.MsgLikeKind
import org.matrix.rustcomponents.sdk.RoomMessageEventContentWithoutRelation
import org.matrix.rustcomponents.sdk.Timeline
import org.matrix.rustcomponents.sdk.TimelineItemContent

/**
 * A fresh message carrying [eventId]'s text or media, without its reply or thread. Media keeps
 * its source and keys, so the file is not uploaded again. Null for anything but a message.
 */
internal suspend fun Timeline.forwardableContent(eventId: String): RoomMessageEventContentWithoutRelation? {
    val item = getEventTimelineItemByEventId(eventId)
    try {
        val kind = (item.content as? TimelineItemContent.MsgLike)?.content?.kind as? MsgLikeKind.Message
        return kind?.content?.msgType?.let(::createMessageContent)
    } finally {
        item.destroy()
    }
}
