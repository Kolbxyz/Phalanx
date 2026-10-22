package org.phalanx.android.core.matrix

import org.junit.Assert.assertEquals
import org.junit.Test

class HtmlMarksTest {
    @Test
    fun `marks user pills and prefixes the name`() {
        assertEquals(
            "hi <annotation mention=\"@ann:s.org\">@Ann</annotation>",
            HtmlMarks.mark("hi <a href=\"https://matrix.to/#/%40ann%3As.org?via=s.org\">Ann</a>")
        )
    }

    @Test
    fun `leaves other links alone`() {
        val html = "<a href=\"https://matrix.to/#/#room:s.org\">#room</a> <a href=\"https://a.org\">a</a>"
        assertEquals(html, HtmlMarks.mark(html))
    }

    @Test
    fun `marks inline code and keeps code blocks' lines`() {
        assertEquals("<tt><annotation code=\"inline\">a&lt;b</annotation></tt>", HtmlMarks.mark("<code>a&lt;b</code>"))
        assertEquals(
            "<div><tt><annotation code=\"block\">if&nbsp;x<br>&nbsp;&nbsp;y</annotation></tt></div>",
            HtmlMarks.mark("<pre><code class=\"language-kotlin\">if x\n  y\n</code></pre>")
        )
    }

    @Test
    fun `marks spoilers up to their own closing span`() {
        assertEquals(
            "a <annotation spoiler=\"1\">b <span style=\"color:#ff0000\">c</span></annotation> d",
            HtmlMarks.mark("a <span data-mx-spoiler=\"why\">b <span style=\"color:#ff0000\">c</span></span> d")
        )
    }
}
