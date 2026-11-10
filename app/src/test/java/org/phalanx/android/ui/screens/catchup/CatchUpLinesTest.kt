package org.phalanx.android.ui.screens.catchup

import org.junit.Assert.assertEquals
import org.junit.Test
import org.phalanx.android.core.ai.CatchUpLine
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.model.ReplyPreview
import org.phalanx.android.data.model.SendState
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.data.repository.AccountProfile

class CatchUpLinesTest {
    private val hugo = AccountProfile("@hugo:example.org", "Hugo Martin", null)

    @Test
    fun `read messages come first, the reader's own signed as you`() = assertEquals(
        listOf(
            CatchUpLine("You", "on my way", isOwn = true, isUnread = false, toReader = false),
            CatchUpLine("Ana", "ok", isOwn = false, isUnread = true, toReader = false)
        ),
        lines(before = listOf(message("1", "on my way", isOwn = true)), unread = listOf(message("2", "ok")))
    )

    @Test
    fun `a message saying the reader's first name is addressed to them, whatever the case`() = assertEquals(
        listOf(true, true, false, false),
        lines(
            unread = listOf(
                message("1", "hugo tu viens ?"),
                message("2", "Tell Hugo: 9h."),
                message("3", "Hugolin est là"),
                message("4", "Hugo is late", isOwn = true)
            )
        ).map { it.toReader }
    )

    @Test
    fun `a mention pill of the reader is addressed to them`() = assertEquals(
        listOf(true, false),
        lines(
            unread = listOf(
                message("1", "can you?", html = "<annotation mention=\"@hugo:example.org\">@H</annotation> can you?"),
                message("2", "can you?", html = "<annotation mention=\"@ana:example.org\">@Ana</annotation> can you?")
            )
        ).map { it.toReader }
    )

    @Test
    fun `an answer to one of the reader's messages is addressed to them`() = assertEquals(
        listOf(false, true, false),
        lines(
            before = listOf(message("1", "who drives?", isOwn = true)),
            unread = listOf(message("2", "me", replyTo = "\$1"), message("3", "nice", replyTo = "\$2"))
        ).map { it.toReader }
    )

    @Test
    fun `without a name of at least three letters only pills and answers count`() = assertEquals(
        listOf(false),
        lines(
            unread = listOf(message("1", "Al, are you there?")),
            reader = AccountProfile("@al:example.org", "Al", null)
        ).map { it.toReader }
    )

    private fun lines(
        before: List<TimelineEntry.Message> = emptyList(),
        unread: List<TimelineEntry.Message>,
        reader: AccountProfile? = hugo
    ) = catchUpLines(before, unread, "You", reader) { "label" }

    private fun message(
        id: String,
        text: String,
        isOwn: Boolean = false,
        html: String? = null,
        replyTo: String? = null
    ) = TimelineEntry.Message(
        id = id, eventId = "\$$id", transactionId = null, senderId = "@a:b", senderName = "Ana",
        senderAvatar = null, isOwn = isOwn, body = MessageBody.Text(text, html, MessageBody.TextStyle.Normal),
        replyTo = replyTo?.let { ReplyPreview("Someone", null, eventId = it) }, reactions = emptyList(),
        timestampMillis = 0, isEdited = false, sendState = SendState.Sent, canReply = true, canEdit = true
    )
}
