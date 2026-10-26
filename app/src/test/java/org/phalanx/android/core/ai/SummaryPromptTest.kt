package org.phalanx.android.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryPromptTest {
    @Test
    fun `lists the messages oldest first, numbered`() {
        val prompt = SummaryPrompt.of(listOf(SummaryLine("Ana", "hi", "\$a"), SummaryLine("Bo", "hello", "\$b")))
        assertEquals("Conversation:\n[1] Ana: hi\n[2] Bo: hello", prompt.user)
        assertEquals(listOf("\$a", "\$b"), prompt.eventIds)
    }

    @Test
    fun `keeps the newest messages when they do not all fit`() {
        val lines = (1..200).map { SummaryLine("Ana", "message $it " + "x".repeat(100)) }
        val user = SummaryPrompt.of(lines).user
        assertTrue(user.contains("message 200 "))
        assertFalse(user.contains("message 1 "))
        assertTrue(user.length < 10_600)
    }

    @Test
    fun `counts only the messages that fit`() {
        val lines = (1..200).map { SummaryLine("Ana", "message $it " + "x".repeat(100)) }
        val prompt = SummaryPrompt.of(lines)
        assertEquals(prompt.user.lines().size - 1, prompt.count)
        assertTrue(prompt.count < 200)
    }

    @Test
    fun `cuts long messages shorter to keep every one of a busy stretch`() {
        val lines = (1..60).map { SummaryLine("Ana", "message $it " + "x".repeat(380)) }
        val user = SummaryPrompt.of(lines).user
        assertTrue(user.contains("message 1 "))
        assertTrue(user.contains("message 60 "))
    }

    @Test
    fun `cuts a long message and joins its lines`() {
        val user = SummaryPrompt.of(listOf(SummaryLine("Ana", "a\n" + "b".repeat(1_000)))).user
        assertFalse(user.removePrefix("Conversation:\n").contains('\n'))
        assertTrue(user.length < 450)
    }

    @Test
    fun `always keeps the newest message`() {
        val user = SummaryPrompt.of(listOf(SummaryLine("Ana", "only"))).user
        assertTrue(user.endsWith("[1] Ana: only"))
    }
}
