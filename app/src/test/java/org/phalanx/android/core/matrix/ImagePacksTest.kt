package org.phalanx.android.core.matrix

import org.junit.Assert.assertEquals
import org.junit.Test

class ImagePacksTest {
    @Test
    fun `reads emoji and leaves stickers and bad entries out`() {
        val pack = """
            {"images": {
                "blobcat": {"url": "mxc://s.org/a"},
                "wave": {"url": "mxc://s.org/b", "usage": ["sticker"]},
                "both": {"url": "mxc://s.org/c", "usage": ["sticker", "emoticon"]},
                "web": {"url": "https://s.org/d"},
                "has space": {"url": "mxc://s.org/e"}
            }}
        """
        assertEquals(listOf("blobcat" to "mxc://s.org/a", "both" to "mxc://s.org/c"), ImagePacks.emotesOf(pack))
    }

    @Test
    fun `a sticker pack gives no emoji unless an image says otherwise`() {
        val pack = """
            {"pack": {"usage": ["sticker"]}, "images": {
                "wave": {"url": "mxc://s.org/b"},
                "blobcat": {"url": "mxc://s.org/a", "usage": ["emoticon"]}
            }}
        """
        assertEquals(listOf("blobcat" to "mxc://s.org/a"), ImagePacks.emotesOf(pack))
    }

    @Test
    fun `broken content reads as no pack`() {
        assertEquals(emptyList<Pair<String, String>>(), ImagePacks.emotesOf("not json"))
        assertEquals(emptyList<Pair<String, String>>(), ImagePacks.emotesOf("""{"images": []}"""))
    }

    @Test
    fun `global packs list rooms and their state keys`() {
        val global = """{"rooms": {"!a:s.org": {"": {}, "cats": {}}, "!b:s.org": {}}}"""
        assertEquals(mapOf("!a:s.org" to setOf("", "cats"), "!b:s.org" to emptySet()), ImagePacks.globalPacksOf(global))
    }
}
