package org.phalanx.android.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class LinkTrackersTest {
    private fun strips(from: String, to: String) = assertEquals(to, LinkTrackers.strip(from))

    private fun keeps(text: String) = assertEquals(text, LinkTrackers.strip(text))

    @Test
    fun `text without a query is returned as is`() {
        val text = "see https://example.org/page and you"
        assertSame(text, LinkTrackers.strip(text))
    }

    @Test
    fun `drops campaign and click ids, keeping other parameters in order`() {
        strips(
            "https://example.org/a?id=4&utm_source=news&utm_Medium=mail&fbclid=XyZ&lang=fr",
            "https://example.org/a?id=4&lang=fr"
        )
    }

    @Test
    fun `drops the question mark once nothing is left`() {
        strips("https://example.org/a?utm_source=x&gclid=y", "https://example.org/a")
        strips("https://example.org/a?utm_source=x#part", "https://example.org/a#part")
    }

    @Test
    fun `site rules apply only on that site and its subdomains`() {
        strips("https://youtu.be/abc?si=TRACK&t=42", "https://youtu.be/abc?t=42")
        strips("https://music.youtube.com/watch?v=abc&si=TRACK", "https://music.youtube.com/watch?v=abc")
        strips("https://x.com/u/status/1?s=20&t=abc", "https://x.com/u/status/1")
        strips(
            "https://www.amazon.fr/dp/B01?psc=1&pd_rd_r=z&tag=friend-21",
            "https://www.amazon.fr/dp/B01?psc=1&tag=friend-21"
        )
        keeps("https://example.org/watch?si=1&s=2&t=3")
        keeps("https://notyoutube.com/v?si=1")
    }

    @Test
    fun `parameters that change the page are kept`() {
        keeps("https://github.com/o/r/blob/x.kt?ref=main")
        keeps("https://www.nytimes.com/a.html?unlocked_article_code=abc")
        keeps("https://matrix.to/#/!room:s.org?via=s.org")
        keeps("https://app.example.org/#/route?utm_source=x")
        keeps("https://example.org/?q=a%26utm_source%3Dx")
    }

    @Test
    fun `punctuation and markdown around a link are left alone`() {
        strips("Look: https://example.org/?utm_source=x.", "Look: https://example.org/.")
        strips("(see https://example.org/?a=1&fbclid=z)", "(see https://example.org/?a=1)")
        strips("[docs](https://example.org/d?utm_campaign=z)", "[docs](https://example.org/d)")
        strips("[https://a.org/?fbclid=1](https://a.org/?fbclid=1)", "[https://a.org/](https://a.org/)")
        strips("**https://example.org/?gclid=1**", "**https://example.org/**")
        strips(
            "https://en.wikipedia.org/wiki/Fork_(software)?utm_source=x",
            "https://en.wikipedia.org/wiki/Fork_(software)"
        )
    }

    @Test
    fun `links in code are sent as written`() {
        keeps("`curl https://example.org/?utm_source=x`")
        keeps("```\nhttps://example.org/?utm_source=x\n```")
        strips(
            "`code` https://example.org/?utm_source=x `more`",
            "`code` https://example.org/ `more`"
        )
    }

    @Test
    fun `facebook's indexed names, hosts with ports and odd casing`() {
        strips("https://www.facebook.com/p?id=1&__cft__[0]=abc&__tn__=R", "https://www.facebook.com/p?id=1")
        strips("HTTPS://User@Example.ORG:8443/a?UTM_SOURCE=x&b=2", "HTTPS://User@Example.ORG:8443/a?b=2")
    }

    @Test
    fun `every link in a message is cleaned`() {
        strips(
            "a https://a.org/?fbclid=1 b https://b.org/?x=1&utm_id=2\nc https://youtu.be/v?si=3",
            "a https://a.org/ b https://b.org/?x=1\nc https://youtu.be/v"
        )
    }

    @Test
    fun `the message keeps its links clean in the body and the html`() {
        val message = OutgoingMessage.of(
            "**read** https://example.org/?utm_source=x",
            emptyList(),
            formatting = true,
            stripTrackers = true
        )
        assertEquals("**read** https://example.org/", message.body)
        assertFalse(message.html.orEmpty().contains("utm_source"))
    }
}
