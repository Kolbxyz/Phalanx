package org.phalanx.android.core.matrix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReactionKeyTest {
    @Test
    fun `the key comes from the relation`() = assertEquals(
        "😂",
        keyOf(
            """{"type":"m.reaction","content":{"m.relates_to":{"rel_type":"m.annotation",""" +
                """"event_id":"${'$'}a","key":"😂"}}}"""
        )
    )

    @Test
    fun `an event still encrypted has no key`() =
        assertNull(keyOf("""{"type":"m.room.encrypted","content":{"algorithm":"m.megolm.v1.aes-sha2"}}"""))

    @Test
    fun `a long key is cut`() = assertEquals(
        32,
        keyOf("""{"content":{"m.relates_to":{"key":"${"x".repeat(100)}"}}}""")?.length
    )

    @Test
    fun `text that is not json gives nothing`() = assertNull(keyOf("not json"))

    @Test
    fun `a key that is not a string gives nothing`() =
        assertNull(keyOf("""{"content":{"m.relates_to":{"key":{"a":1}}}}"""))
}
