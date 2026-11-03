package org.phalanx.android.ui.screens.room

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.model.PollAnswer

class PollVotesTest {
    private fun poll(max: Int = 1, mine: Set<String> = emptySet(), ended: Boolean = false, disclosed: Boolean = true) =
        MessageBody.Poll(
            question = "Lunch?",
            answers = listOf(PollAnswer("a", "Pizza", 2), PollAnswer("b", "Sushi", 1), PollAnswer("c", "Salad", 0)),
            maxSelections = max,
            disclosed = disclosed,
            ended = ended,
            myAnswers = mine
        )

    @Test
    fun `a single choice votes for the tapped answer`() = assertEquals(listOf("b"), poll().voteAfterTapping("b"))

    @Test
    fun `a single choice moves the vote`() = assertEquals(listOf("c"), poll(mine = setOf("a")).voteAfterTapping("c"))

    @Test
    fun `tapping the chosen answer takes the vote back`() =
        assertEquals(emptyList<String>(), poll(mine = setOf("a")).voteAfterTapping("a"))

    @Test
    fun `several choices add in the poll order`() =
        assertEquals(listOf("a", "c"), poll(max = 3, mine = setOf("c")).voteAfterTapping("a"))

    @Test
    fun `several choices remove the tapped one`() =
        assertEquals(listOf("b"), poll(max = 3, mine = setOf("a", "b")).voteAfterTapping("a"))

    @Test
    fun `a full selection ignores another answer`() =
        assertNull(poll(max = 2, mine = setOf("a", "b")).voteAfterTapping("c"))

    @Test
    fun `an ended poll takes no vote`() = assertNull(poll(ended = true).voteAfterTapping("a"))

    @Test
    fun `an unknown answer is ignored`() = assertNull(poll().voteAfterTapping("z"))

    @Test
    fun `undisclosed results show only once ended`() {
        assertFalse(poll(disclosed = false).showsResults)
        assertTrue(poll(disclosed = false, ended = true).showsResults)
    }

    @Test
    fun `the total counts every vote`() = assertEquals(3, poll().totalVotes)
}
