package org.phalanx.android.core.matrix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.phalanx.android.data.model.ArchivedKind

class ArchiveChangeTest {
    private fun message(content: String) = archiveChange(ArchivedEventType.Message, content)

    private fun redaction(content: String) = archiveChange(ArchivedEventType.Redaction, content)

    @Test
    fun `a text message is kept as sent`() {
        val change = message("""{"msgtype":"m.text","body":"hello"}""")
        assertEquals(ArchiveChange.Sent(ArchivedKind.Text, "hello"), change)
    }

    @Test
    fun `a reply drops its quoted fallback`() {
        val content = """{"msgtype":"m.text","body":"> <@bob:example.org> hi\n\nhello",
            "m.relates_to":{"m.in_reply_to":{"event_id":"${'$'}parent"}}}"""
        assertEquals(
            ArchiveChange.Sent(ArchivedKind.Text, "hello"),
            message(content)
        )
    }

    @Test
    fun `media keeps its caption or file name`() {
        val change = message("""{"msgtype":"m.image","body":"cat.png"}""")
        assertEquals(ArchiveChange.Sent(ArchivedKind.Image, "cat.png"), change)
    }

    @Test
    fun `an edit takes the replacement text, not the starred fallback`() {
        val content = """{"msgtype":"m.text","body":"* fixed",
            "m.new_content":{"msgtype":"m.text","body":"fixed"},
            "m.relates_to":{"rel_type":"m.replace","event_id":"${'$'}original"}}"""
        assertEquals(
            ArchiveChange.Edited("\$original", ArchivedKind.Text, "fixed"),
            message(content)
        )
    }

    @Test
    fun `a redaction names the deleted event`() {
        val change = redaction("""{"redacts":"${'$'}gone"}""")
        assertEquals(ArchiveChange.Deleted("\$gone"), change)
    }

    @Test
    fun `a redaction without its target changes nothing`() {
        assertNull(redaction("{}"))
    }

    @Test
    fun `unreadable content changes nothing`() {
        assertNull(message("not json"))
        assertNull(message("[1]"))
    }
}
