package org.phalanx.android.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.model.RoomSummary

class CatchUpTest {
    @Test
    fun `a catch-up reads its points and lines`() = assertEquals(
        CatchUp(listOf("Dinner moves to Friday 20h"), "Can you bring wine?", null),
        catchUpOf("""{"points":["Dinner moves to Friday 20h "],"waiting":"Can you bring wine?","promised":" "}""")
    )

    @Test
    fun `a reply that is not the shape asked for gives nothing`() = assertNull(catchUpOf("Sure!"))

    @Test
    fun `mentions come first, then direct chats and questions, and muted rooms not at all`() {
        val now = 100 * HOUR
        val rooms = listOf(
            room("busy group", unread = 40, lastHoursAgo = 1),
            room("mention", unread = 2, mentions = 1, lastHoursAgo = 30),
            room("dm", unread = 1, direct = true, lastHoursAgo = 5),
            room("question", unread = 3, preview = "are you coming?", lastHoursAgo = 2),
            room("muted", unread = 99, mentions = 3, muted = true, lastHoursAgo = 0),
            room("read", unread = 0, lastHoursAgo = 0)
        )
        assertEquals(
            listOf("mention", "dm", "busy group", "question"),
            roomsToCatchUp(rooms, now).map { it.id }
        )
    }

    private fun room(
        id: String,
        unread: Long,
        lastHoursAgo: Long,
        mentions: Long = 0,
        direct: Boolean = false,
        muted: Boolean = false,
        preview: String = "ok"
    ) = RoomSummary(
        id = id,
        name = id,
        isDirect = direct,
        avatar = null,
        preview = MessageBody.Text(preview, null, MessageBody.TextStyle.Normal),
        previewIsOwn = false,
        previewSender = null,
        lastActivityMillis = 100 * HOUR - lastHoursAgo * HOUR,
        unreadCount = unread,
        highlightCount = mentions,
        isFavourite = false,
        isMuted = muted
    )

    private companion object {
        const val HOUR = 3_600_000L
    }
}
