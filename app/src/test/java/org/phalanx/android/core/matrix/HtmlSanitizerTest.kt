package org.phalanx.android.core.matrix

import org.junit.Assert.assertEquals
import org.junit.Test

class HtmlSanitizerTest {
    @Test
    fun `drops the rich reply fallback`() {
        val html = "<mx-reply><blockquote><a href=\"https://matrix.to/#/!r/\$e\">In reply to</a> " +
            "<a href=\"https://matrix.to/#/@a:b\">@a:b</a><br>original</blockquote></mx-reply>The answer"
        assertEquals("The answer", HtmlSanitizer.sanitize(html))
    }

    @Test
    fun `replaces custom emotes with their alt text`() {
        val html = "hi <img data-mx-emoticon src=\"mxc://s/abc\" alt=\":wave:\" height=\"32\"> there"
        assertEquals("hi :wave: there", HtmlSanitizer.sanitize(html))
    }

    @Test
    fun `marks custom emotes for inline images when asked`() {
        val html = "hi <img data-mx-emoticon src=\"mxc://s/abc\" alt=\":wave:\" height=\"32\"> there"
        val sanitized = HtmlSanitizer.sanitizeWithEmotes(html)
        assertEquals("hi <annotation emote=\"0\">:wave:</annotation> there", sanitized.html)
        assertEquals(listOf("mxc://s/abc"), sanitized.emoteUrls)
    }

    @Test
    fun `keeps alt text for an image that is not on the homeserver`() {
        val sanitized = HtmlSanitizer.sanitizeWithEmotes("<img src=\"https://x.org/a.png\" alt=\":a:\">")
        assertEquals(":a:", sanitized.html)
        assertEquals(emptyList<String>(), sanitized.emoteUrls)
    }

    @Test
    fun `drops an image without alt text`() {
        assertEquals("a  b", HtmlSanitizer.sanitize("a <img src=\"mxc://s/x\"/> b"))
    }

    @Test
    fun `maps Matrix and font colours to css`() {
        assertEquals(
            "<span style=\"color:#ff0000\">red</span>",
            HtmlSanitizer.sanitize("<span data-mx-color=\"#ff0000\">red</span>")
        )
        assertEquals(
            "<span style=\"color:#00ff00\">green</span>",
            HtmlSanitizer.sanitize("<font color=\"#00ff00\">green</font>")
        )
    }

    @Test
    fun `removes script and style blocks`() {
        assertEquals("ok", HtmlSanitizer.sanitize("<script>alert(1)</script>ok<style>p{}</style>"))
    }

    @Test
    fun `keeps ordinary formatting`() {
        val html = "<b>bold</b> <i>it</i> <a href=\"https://example.org\">link</a><br><del>x</del>"
        assertEquals(html, HtmlSanitizer.sanitize(html))
    }

    @Test
    fun `strips the plain reply fallback`() {
        val body = "> <@alice:example.org> where are you?\n> second line\n\nAt home"
        assertEquals("At home", HtmlSanitizer.stripPlainReplyFallback(body))
    }

    @Test
    fun `leaves plain bodies without a fallback alone`() {
        assertEquals("no quote here", HtmlSanitizer.stripPlainReplyFallback("no quote here"))
        assertEquals("> only a quote", HtmlSanitizer.stripPlainReplyFallback("> only a quote"))
    }
}
