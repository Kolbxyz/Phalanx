package org.phalanx.android.ui.screens.room

import org.junit.Assert.assertFalse
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
}
