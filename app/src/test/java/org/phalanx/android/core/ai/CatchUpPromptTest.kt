package org.phalanx.android.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatchUpPromptTest {
    @Test
    fun `a group offers the messages addressed to the reader, and the reader's own`() {
        val prompt = CatchUpPrompt.of(
            listOf(
                read("Hugo", "I'll bring the wrench", isOwn = true),
                unread("Ana", "ride moves to 9:00"),
                unread("Bo", "Hugo, how much do I owe you?", toReader = true),
                unread("Cy", "see you there")
            ),
            you = "Hugo",
            isDirect = false
        )
        assertEquals(listOf(3), prompt.asks)
        assertEquals(listOf(1), prompt.promises)
        assertEquals(
            "Already read:\n[1] Hugo: I'll bring the wrench\n\n" +
                "Unread:\n[2] Ana: ride moves to 9:00\n[3] Bo: Hugo, how much do I owe you?\n" +
                "[4] Cy: see you there\n\n" +
                "Addressed to Hugo:\n[3] Bo: Hugo, how much do I owe you?\n\n" +
                "Written by Hugo:\n[1] Hugo: I'll bring the wrench",
            prompt.user
        )
        assertTrue(prompt.system.contains("The reader appears as \"Hugo\"."))
    }

    @Test
    fun `a message the reader wrote after is not waiting on them`() {
        val prompt = CatchUpPrompt.of(
            listOf(
                unread("Ana", "Hugo, are you coming?", toReader = true),
                unread("Hugo", "yes", isOwn = true),
                unread("Ana", "Hugo, can you drive?", toReader = true)
            ),
            you = "Hugo",
            isDirect = false
        )
        assertEquals(listOf(3), prompt.asks)
    }

    @Test
    fun `a direct chat offers every message since the reader last wrote`() {
        val prompt = CatchUpPrompt.of(
            listOf(read("You", "ok", isOwn = true), unread("Ana", "call me tonight"), unread("Ana", "it's about Sam")),
            you = "You",
            isDirect = true
        )
        assertEquals(listOf(2, 3), prompt.asks)
        assertTrue(prompt.system.contains("The reader appears as \"You\"."))
    }

    @Test
    fun `nothing addressed to the reader leaves only the points to write`() {
        val lines = listOf(unread("Ana", "hi"), unread("Bo", "hello"))
        val prompt = CatchUpPrompt.of(lines, "Hugo", isDirect = false)
        assertEquals("Unread:\n[1] Ana: hi\n[2] Bo: hello", prompt.user)
        assertFalse(prompt.system.contains("ask_line"))
        assertFalse(prompt.system.contains("promise_line"))
        assertEquals(
            """
            root ::= ( "{" ws "\"points\":" ws "[" ws text ( ws "," ws text ){0,2} ws "]"
            ws "}" )
            text ::= "\"" char{1,160} "\""
            char ::= [^"\\*\x00-\x1F] | "\\" ["\\/nt]
            ws ::= [ \t\n]{0,12}
            """.trimIndent(),
            prompt.grammar
        )
    }

    @Test
    fun `the grammar lets the model pick only an offered line, or none`() {
        val lines = listOf(
            unread("Hugo", "I'll send it", isOwn = true),
            unread("Ana", "Hugo?", toReader = true),
            unread("Bo", "Hugo, the plan?", toReader = true)
        )
        val grammar = CatchUpPrompt.of(lines, "Hugo", isDirect = false).grammar.orEmpty()
        assertTrue(
            grammar.contains(
                """ws "," ws ( "\"ask_line\":" ws "0" ws "," ws "\"ask\":" ws "null" | """ +
                    """"\"ask_line\":" ws ( "2" | "3" ) ws "," ws "\"ask\":" ws text )"""
            )
        )
        assertTrue(grammar.contains(""""\"promise_line\":" ws ( "1" ) ws "," ws "\"promise\":" ws text )"""))
    }

    @Test
    fun `the schema for Claude offers the same lines as the grammar`() {
        val lines = listOf(unread("Hugo", "I'll send it", isOwn = true), unread("Ana", "Hugo?", toReader = true))
        val schema = CatchUpPrompt.of(lines, "Hugo", isDirect = false).schema.toString()
        assertEquals(
            """{"type":"object","properties":{"language":{"type":"string"},""" +
                """"points":{"type":"array","items":{"type":"string"}},""" +
                """"ask_line":{"type":"integer","enum":[0,2]},"ask":{"anyOf":[{"type":"string"},{"type":"null"}]},""" +
                """"promise_line":{"type":"integer","enum":[0,1]},""" +
                """"promise":{"anyOf":[{"type":"string"},{"type":"null"}]}},""" +
                """"required":["language","points","ask_line","ask","promise_line","promise"],""" +
                """"additionalProperties":false}""",
            schema
        )
    }

    @Test
    fun `only the newest candidates are offered`() {
        val lines = (1..10).map { unread("Ana", "Hugo $it?", toReader = true) }
        assertEquals((5..10).toList(), CatchUpPrompt.of(lines, "Hugo", isDirect = false).asks)
    }

    @Test
    fun `reads back only what the code offered`() {
        val prompt = CatchUpPrompt.of(listOf(unread("Ana", "Hugo?", toReader = true)), "Hugo", isDirect = false)
        assertEquals(
            CatchUp(listOf("Ana is looking for Hugo"), "Ana asks where you are", null),
            prompt.read("""{"points":["Ana is looking for Hugo"],"ask_line":1,"ask":"Ana asks where you are"}""")
        )
    }

    private fun read(sender: String, text: String, isOwn: Boolean = false) =
        CatchUpLine(sender, text, isOwn, isUnread = false, toReader = false)

    private fun unread(sender: String, text: String, isOwn: Boolean = false, toReader: Boolean = false) =
        CatchUpLine(sender, text, isOwn, isUnread = true, toReader = toReader)
}
