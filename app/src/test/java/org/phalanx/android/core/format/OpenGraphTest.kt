package org.phalanx.android.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenGraphTest {
    @Test
    fun `reads open graph tags in either quote style and attribute order`() {
        val html = """
            <html><head>
            <meta property="og:title" content="Caf&eacute; &amp; Co &#8212; Menu">
            <meta content='Fresh bread &quot;daily&quot;' property='og:description' />
            <meta property="og:site_name" content="Example">
            <meta property="og:image" content="/img/card.png">
            </head></html>
        """.trimIndent()
        val graph = OpenGraph.fromHtml(html, "https://example.org/menu/today")!!
        assertEquals("Caf&eacute; & Co — Menu", graph.title)
        assertEquals("Fresh bread \"daily\"", graph.description)
        assertEquals("Example", graph.siteName)
        assertEquals("https://example.org/img/card.png", graph.image)
    }

    @Test
    fun `falls back to twitter tags, the description and the title element`() {
        val html = """
            <title>Plain title</title>
            <meta name="description" content="Plain description">
            <meta name="twitter:image" content="https://cdn.example.org/a.jpg">
        """.trimIndent()
        val graph = OpenGraph.fromHtml(html, "https://example.org/")!!
        assertEquals("Plain title", graph.title)
        assertEquals("Plain description", graph.description)
        assertEquals("https://cdn.example.org/a.jpg", graph.image)
    }

    @Test
    fun `the first of a repeated tag wins and plain http images are dropped`() {
        val html = """
            <meta property="og:title" content="First">
            <meta property="og:title" content="Second">
            <meta property="og:image" content="http://example.org/a.jpg">
        """.trimIndent()
        val graph = OpenGraph.fromHtml(html, "https://example.org/")!!
        assertEquals("First", graph.title)
        assertNull(graph.image)
    }

    @Test
    fun `a page with nothing to say has no preview`() {
        assertNull(OpenGraph.fromHtml("<html><body>hi</body></html>", "https://example.org/"))
    }

    @Test
    fun `reads the homeserver's answer, keeping only mxc images`() {
        val json = """{"og:title":"T","og:description":"D","og:image":"mxc://s.org/abc","matrix:image:size":12}"""
        val graph = OpenGraph.fromJson(json)!!
        assertEquals("T", graph.title)
        assertEquals("mxc://s.org/abc", graph.image)
        assertNull(OpenGraph.fromJson("""{"og:image":"https://x.org/a.png"}"""))
        assertNull(OpenGraph.fromJson("[1,2]"))
        assertNull(OpenGraph.fromJson("not json"))
    }
}
