package org.phalanx.android.ui.screens.room

import org.junit.Assert.assertEquals
import org.junit.Test
import org.phalanx.android.data.model.MessageBody

class MessageLinksTest {
    private fun text(text: String, html: String? = null) = MessageBody.Text(text, html, MessageBody.TextStyle.Normal)

    @Test
    fun `anchors are read from the html, decoded`() {
        val body = text("x", """see <a href="https://a.example/?q=1&amp;r=2">this</a>""")
        assertEquals(listOf("https://a.example/?q=1&r=2"), linksOf(body))
    }

    @Test
    fun `plain text urls lose trailing punctuation`() {
        assertEquals(listOf("https://a.example/page"), linksOf(text("look at https://a.example/page.")))
    }

    @Test
    fun `each link once, at most three`() {
        val body = text("https://a.example https://a.example https://b.example https://c.example https://d.example")
        assertEquals(listOf("https://a.example", "https://b.example", "https://c.example"), linksOf(body))
    }

    @Test
    fun `other schemes and media without a caption have none`() {
        assertEquals(emptyList<String>(), linksOf(text("x", """<a href="javascript:alert(1)">x</a>""")))
        assertEquals(emptyList<String>(), linksOf(MessageBody.Location("geo:0,0", null)))
    }
}
