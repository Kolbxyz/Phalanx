package org.phalanx.android.core.matrix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.model.SendState
import org.phalanx.android.data.model.TimelineEntry

class ReadersTest {
    @Test
    fun `nobody has read anything when the user sent nothing`() =
        assertNull(readersOfLatestOwn(listOf(message("1", own = false, readBy = setOf("@bo:x")))))

    @Test
    fun `a receipt on the user's latest message or after it counts`() =
        assertEquals(
            Readers("\$2", setOf("@ana:x", "@bo:x")),
            readersOfLatestOwn(
                listOf(
                    message("1", own = true, readBy = setOf("@cy:x")),
                    message("2", own = true, readBy = setOf("@ana:x")),
                    message("3", own = false, readBy = setOf("@bo:x"))
                )
            )
        )

    @Test
    fun `a message still sending is not the latest`() =
        assertEquals(
            Readers("\$1", emptySet<String>()),
            readersOfLatestOwn(listOf(message("1", own = true), message("2", own = true, sent = false)))
        )

    private fun message(id: String, own: Boolean, readBy: Set<String> = emptySet(), sent: Boolean = true) =
        TimelineEntry.Message(
            id = id, eventId = if (sent) "\$$id" else null, transactionId = null, senderId = "@a:x",
            senderName = "A", senderAvatar = null, isOwn = own,
            body = MessageBody.Text("x", null, MessageBody.TextStyle.Normal), replyTo = null,
            reactions = emptyList(), timestampMillis = 0, isEdited = false,
            sendState = if (sent) SendState.Sent else SendState.Sending, canReply = true, canEdit = true,
            readBy = readBy
        )
}
