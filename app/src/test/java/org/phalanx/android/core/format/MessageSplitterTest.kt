package org.phalanx.android.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phalanx.android.data.model.AppSettings
import org.phalanx.android.data.model.Bridge

class MessageSplitterTest {
    private val limit = 100

    @Test
    fun `a message that fits is left whole`() {
        assertEquals(listOf("hello"), MessageSplitter.split("hello", limit))
    }

    @Test
    fun `cuts between paragraphs before lines and words`() {
        val first = "a".repeat(60)
        val second = "b ".repeat(30).trim()
        assertEquals(listOf(first, second), MessageSplitter.split("$first\n\n$second", limit))
    }

    @Test
    fun `cuts between words when a paragraph is too long`() {
        val parts = MessageSplitter.split("word ".repeat(50).trim(), limit)
        assertTrue(parts.all { it.length <= limit && !it.startsWith(" ") && !it.endsWith(" ") })
        assertEquals("word ".repeat(50).trim(), parts.joinToString(" "))
    }

    @Test
    fun `cuts through a word longer than a part, never through an emoji`() {
        val text = "x".repeat(limit - CLOSING_ROOM - 1) + "😀".repeat(20)
        val parts = MessageSplitter.split(text, limit)
        assertEquals(text, parts.joinToString(""))
        assertTrue(parts.none { it.first().isLowSurrogate() || it.last().isHighSurrogate() })
    }

    @Test
    fun `a code block cut in two is closed and reopened`() {
        val code = (1..40).joinToString("\n") { "line $it" }
        val parts = MessageSplitter.split("```kotlin\n$code\n```", limit)
        assertTrue(parts.size > 1)
        parts.forEach { part ->
            assertTrue(part.length <= limit)
            assertEquals(2, part.lines().count { it.startsWith("```") })
        }
        assertTrue(parts.drop(1).all { it.startsWith("```kotlin\n") })
    }

    @Test
    fun `each part keeps the emote or silent prefix`() {
        val long = "/me " + "waves ".repeat(400).trim()
        val parts = OutgoingMessage.parts(long, AppSettings(), Bridge.Discord)
        assertTrue(parts.size > 1)
        assertTrue(parts.all { it.startsWith("/me ") && it.length <= 2_000 })
    }

    @Test
    fun `nothing is split when the setting is off or the bridge has no limit`() {
        val long = "a ".repeat(3_000)
        assertEquals(1, OutgoingMessage.parts(long, AppSettings(splitLongMessages = false), Bridge.Discord).size)
        assertEquals(1, OutgoingMessage.parts(long, AppSettings(), Bridge.Signal).size)
        assertEquals(1, OutgoingMessage.parts(long, AppSettings(), null).size)
    }

    private companion object {
        const val CLOSING_ROOM = 4
    }
}
