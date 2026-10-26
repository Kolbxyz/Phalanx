package org.phalanx.android.core.matrix

import org.junit.Assert.assertEquals
import org.junit.Test
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.model.SendState
import org.phalanx.android.data.model.TimelineEntry

class TimelineRowsTest {
    @Test
    fun `a receipt on a hidden item counts on the message before it`() {
        val entries = entriesOf(
            listOf(
                TimelineRow(message("1"), setOf("@ana:x")),
                TimelineRow(null, setOf("@bo:x")),
                TimelineRow(TimelineEntry.DayDivider("d", 0)),
                TimelineRow(null, setOf("@cy:x"))
            )
        )
        assertEquals(listOf("1", "d"), entries.map { it.id })
        assertEquals(setOf("@ana:x", "@bo:x", "@cy:x"), (entries[0] as TimelineEntry.Message).readBy)
    }

    @Test
    fun `a receipt before any message is dropped`() {
        val entries = entriesOf(listOf(TimelineRow(null, setOf("@bo:x")), TimelineRow(message("1"))))
        assertEquals(emptySet<String>(), (entries.single() as TimelineEntry.Message).readBy)
    }

    private fun message(id: String) = TimelineEntry.Message(
        id = id, eventId = "\$$id", transactionId = null, senderId = "@a:x", senderName = "A",
        senderAvatar = null, isOwn = true, body = MessageBody.Text("x", null, MessageBody.TextStyle.Normal),
        replyTo = null, reactions = emptyList(), timestampMillis = 0, isEdited = false,
        sendState = SendState.Sent, canReply = true, canEdit = true
    )
}
