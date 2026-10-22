package org.phalanx.android.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phalanx.android.data.model.Member

class MarkdownTest {
    private fun html(text: String) = Markdown.render(text).html

    @Test
    fun `plain text has no html`() {
        assertNull(html("just words, 5 * 3 * 2\nand a second line"))
    }

    @Test
    fun `renders Discord emphasis`() {
        assertEquals(
            "<strong>b</strong> <em>i</em> <em>i</em> <u>u</u> <del>s</del> <strong><em>bi</em></strong>",
            html("**b** *i* _i_ __u__ ~~s~~ ***bi***")
        )
    }

    @Test
    fun `nests emphasis`() {
        assertEquals("<em>a <strong>b</strong> c</em>", html("*a **b** c*"))
    }

    @Test
    fun `leaves underscores inside words alone`() {
        assertNull(html("snake_case_name"))
    }

    @Test
    fun `renders spoilers as Matrix spoilers`() {
        assertEquals("a <span data-mx-spoiler>secret</span>", html("a ||secret||"))
    }

    @Test
    fun `code is escaped and never formatted`() {
        assertEquals("<code>**a** &lt;b&gt;</code>", html("`**a** <b>`"))
    }

    @Test
    fun `fenced code keeps its lines and language`() {
        assertEquals(
            "x<pre><code class=\"language-kotlin\">val a = 1\n  b</code></pre>y",
            html("x\n```kotlin\nval a = 1\n  b\n```\ny")
        )
    }

    @Test
    fun `renders quotes headings and lists`() {
        assertEquals("<blockquote>a<br><em>b</em></blockquote>", html("> a\n> *b*"))
        assertEquals("<h2>Title</h2>", html("## Title"))
        assertEquals("<ul><li>a</li><li>b</li></ul><ol><li>c</li></ol>", html("- a\n* b\n1. c"))
    }

    @Test
    fun `backslash escapes markdown`() {
        assertEquals("*not italic*", html("\\*not italic\\*"))
    }

    @Test
    fun `escapes html`() {
        assertEquals("<em>&lt;script&gt;</em>", html("*<script>*"))
    }

    @Test
    fun `links urls and markdown links`() {
        assertEquals(
            "see <a href=\"https://a.org/x\">https://a.org/x</a>. <a href=\"https://b.org\">B</a>",
            html("see https://a.org/x. [B](https://b.org)")
        )
    }

    @Test
    fun `picked members become pills and are mentioned`() {
        val ann = Member("@discord_1:s.org", "Ann", null)
        val annLee = Member("@ann:s.org", "Ann Lee", null)
        val rendered = Markdown.render("hi @Ann Lee and @Ann!", listOf(ann, annLee))
        assertEquals(
            "hi <a href=\"https://matrix.to/#/@ann:s.org\">Ann Lee</a> and " +
                "<a href=\"https://matrix.to/#/@discord_1:s.org\">Ann</a>!",
            rendered.html
        )
        assertEquals(listOf("@ann:s.org", "@discord_1:s.org"), rendered.userIds)
    }

    @Test
    fun `typed Matrix IDs are mentioned`() {
        val rendered = Markdown.render("ping @bob:example.org.")
        assertEquals("ping <a href=\"https://matrix.to/#/@bob:example.org\">@bob:example.org</a>.", rendered.html)
        assertEquals(listOf("@bob:example.org"), rendered.userIds)
    }

    @Test
    fun `without formatting only mentions render`() {
        val rendered = Markdown.render("**a** @bob:s.org", formatting = false)
        assertEquals("**a** <a href=\"https://matrix.to/#/@bob:s.org\">@bob:s.org</a>", rendered.html)
        assertNull(Markdown.render("**a**", formatting = false).html)
    }

    @Test
    fun `detects room mentions`() {
        assertTrue(Markdown.mentionsRoom("hey @room look"))
        assertFalse(Markdown.mentionsRoom("mail me@roomy.org"))
    }
}
