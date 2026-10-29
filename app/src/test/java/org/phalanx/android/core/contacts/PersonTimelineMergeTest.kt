package org.phalanx.android.core.contacts

import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.model.SendState
import org.phalanx.android.data.model.TimelineEntry

class PersonTimelineMergeTest {
    private val day = 86_400_000L

    private fun message(id: String, at: Long, own: Boolean = false) = TimelineEntry.Message(
        id = id,
        eventId = "\$$id",
        transactionId = null,
        senderId = if (own) "@me:x" else "@them:x",
        senderName = id,
        senderAvatar = null,
        isOwn = own,
        body = MessageBody.Text(id, null, MessageBody.TextStyle.Normal),
        replyTo = null,
        reactions = emptyList(),
        timestampMillis = at,
        isEdited = false,
        sendState = SendState.Sent,
        canReply = true,
        canEdit = own
    )

    private fun merge(vararg parts: ChatPart) = mergeTimelines(parts.toList(), ZoneOffset.UTC)

    private fun MergedTimeline.ids() = entries.map { it.id }

    @Test
    fun `interleaves chats by time, prefixes row ids and rebuilds day dividers`() {
        val whatsapp = ChatPart("!wa", listOf(message("1", 10), message("2", day + 30)), hasMore = false)
        val signal = ChatPart("!sig", listOf(message("1", 20)), hasMore = false)
        val merged = merge(whatsapp, signal)
        assertEquals(listOf("day-0", "!wa/1", "!sig/1", "day-1", "!wa/2"), merged.ids())
        assertEquals("!sig", merged.origins.getValue("!sig/1").roomId)
        assertEquals("1", merged.origins.getValue("!sig/1").message.id)
    }

    @Test
    fun `shows nothing older than a chat with more history has loaded`() {
        val whatsapp = ChatPart("!wa", listOf(message("old", 10), message("new", 100)), hasMore = false)
        val signal = ChatPart("!sig", listOf(message("mid", 50)), hasMore = true)
        assertEquals(listOf("day-0", "!sig/mid", "!wa/new"), merge(whatsapp, signal).ids())
    }

    @Test
    fun `shows nothing while a chat with history to load has no message yet`() {
        val whatsapp = ChatPart("!wa", listOf(message("1", 10)), hasMore = false)
        val signal = ChatPart("!sig", emptyList(), hasMore = true)
        assertEquals(emptyList<String>(), merge(whatsapp, signal).ids())
        assertEquals("!sig", laggingChat(listOf(whatsapp, signal)))
    }

    @Test
    fun `loads older messages from the chat the thread stops at`() {
        val whatsapp = ChatPart("!wa", listOf(message("1", 10)), hasMore = true)
        val signal = ChatPart("!sig", listOf(message("1", 50)), hasMore = true)
        val matrix = ChatPart("!mx", listOf(message("1", 90)), hasMore = false)
        assertEquals("!sig", laggingChat(listOf(whatsapp, signal, matrix)))
        assertNull(laggingChat(listOf(matrix)))
    }

    @Test
    fun `targets the chat the person last wrote from, not the user's last message`() {
        val whatsapp = ChatPart("!wa", listOf(message("theirs", 10), message("mine", 90, own = true)), false)
        val signal = ChatPart("!sig", listOf(message("theirs", 50)), hasMore = false)
        assertEquals("!sig", lastUsedChat(listOf(whatsapp, signal)))
    }

    @Test
    fun `targets the latest chat when the person never wrote, and none when nothing was said`() {
        val whatsapp = ChatPart("!wa", listOf(message("mine", 10, own = true)), hasMore = false)
        val signal = ChatPart("!sig", listOf(message("mine", 50, own = true)), hasMore = false)
        assertEquals("!sig", lastUsedChat(listOf(whatsapp, signal)))
        assertNull(lastUsedChat(listOf(ChatPart("!wa", emptyList(), false))))
    }

    @Test
    fun `knows when the others last wrote, leaving the user's own messages out`() {
        val whatsapp = ChatPart("!wa", listOf(message("theirs", 10), message("mine", 90, own = true)), false)
        val signal = ChatPart("!sig", listOf(message("theirs", 50)), hasMore = false)
        assertEquals(50L, lastTheirsMillis(listOf(whatsapp, signal)))
        assertNull(lastTheirsMillis(listOf(ChatPart("!wa", listOf(message("mine", 5, own = true)), false))))
    }
}
