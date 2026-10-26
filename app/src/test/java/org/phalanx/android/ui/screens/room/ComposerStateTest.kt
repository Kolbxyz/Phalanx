package org.phalanx.android.ui.screens.room

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.model.OutgoingMedia
import org.phalanx.android.data.model.SendState
import org.phalanx.android.data.model.TimelineEntry

class ComposerStateTest {
    private val image = OutgoingMedia.Image(File("a.jpg"), "image/jpeg", 1, 1)

    @Test
    fun `empty composer shows the microphone`() {
        val state = ComposerState()
        assertTrue(state.showsMic)
        assertFalse(state.canSend)
    }

    @Test
    fun `whitespace alone is nothing to send`() {
        assertTrue(ComposerState().withDraft("  \n").showsMic)
    }

    @Test
    fun `text turns the microphone into send`() {
        val state = ComposerState().withDraft("hi")
        assertFalse(state.showsMic)
        assertTrue(state.canSend)
    }

    @Test
    fun `a ready attachment can be sent without text`() {
        val state = ComposerState(attachments = listOf(PendingAttachment("1", image)))
        assertTrue(state.canSend)
        assertFalse(state.showsMic)
    }

    @Test
    fun `an attachment still being read keeps send disabled and the microphone hidden`() {
        val state = ComposerState(attachments = listOf(PendingAttachment("1", null)))
        assertFalse(state.canSend)
        assertFalse(state.showsMic)
    }

    @Test
    fun `editing never shows the microphone`() {
        assertFalse(ComposerState(mode = ComposerMode.Edit(message())).showsMic)
    }

    private fun message() = TimelineEntry.Message(
        id = "1", eventId = "\$1", transactionId = null, senderId = "@a:b", senderName = "A",
        senderAvatar = null, isOwn = true, body = MessageBody.Text("x", null, MessageBody.TextStyle.Normal),
        replyTo = null, reactions = emptyList(), timestampMillis = 0, isEdited = false,
        sendState = SendState.Sent, canReply = true, canEdit = true
    )
}
