package org.phalanx.android.core.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class AssistPromptsTest {
    @Test
    fun `the user's label is dropped from a reply`() =
        assertEquals(
            listOf("Sure, see you at 8!", "On my way", "Thanks"),
            repliesOf("You: Sure, see you at 8!\n- you : On my way\n\"You: Thanks\"", "You")
        )

    @Test
    fun `a copied message number goes too`() =
        assertEquals(
            listOf("D'accord", "Pas de souci"),
            repliesOf("[12] Vous: D'accord\n2. [13] « Pas de souci »", "Vous")
        )

    @Test
    fun `a colon inside the reply stays`() =
        assertEquals(
            listOf("Note: bring the keys", "Youpi: done"),
            repliesOf("Note: bring the keys\nYoupi: done", "You")
        )

    @Test
    fun `at most three replies are kept`() =
        assertEquals(listOf("a", "b", "c"), repliesOf("1. a\n\n2) b\n* c\n• d", "You"))
}
