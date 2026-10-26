package org.phalanx.android.ui.screens.room

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiOnlyTest {
    @Test
    fun `one emoji is drawn large`() = assertTrue(isEmojiOnly("👍"))

    @Test
    fun `up to three emoji with spaces are drawn large`() = assertTrue(isEmojiOnly(" 🎉 🎉🎉 "))

    @Test
    fun `four emoji are an ordinary message`() = assertFalse(isEmojiOnly("🎉🎉🎉🎉"))

    @Test
    fun `text with an emoji is an ordinary message`() = assertFalse(isEmojiOnly("ok 👍"))

    @Test
    fun `digits are not emoji`() = assertFalse(isEmojiOnly("123"))

    @Test
    fun `blank text is not emoji`() = assertFalse(isEmojiOnly("   "))

    @Test
    fun `splits emoji as the reader sees them`() =
        assertEquals(listOf("👩‍💻", "❤️"), emojiIn(" 👩‍💻❤️ "))

    @Test
    fun `text with an emoji has no emoji list`() = assertNull(emojiIn("ok 👍"))
}
