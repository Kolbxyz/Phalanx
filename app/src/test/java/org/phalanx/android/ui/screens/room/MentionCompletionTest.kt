package org.phalanx.android.ui.screens.room

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.phalanx.android.data.model.Member

class MentionCompletionTest {
    private val ann = Member("@ann:s.org", "Ann Lee", null)
    private val joanna = Member("@discord_9:s.org", "Joanna", null)
    private val members = listOf(joanna, ann)

    @Test
    fun `only completes an at sign starting a word at the end`() {
        assertEquals("an", MentionCompletion.query("hi @an"))
        assertNull(MentionCompletion.query("mail me@an"))
        assertNull(MentionCompletion.query("hi @ann done"))
    }

    @Test
    fun `names starting with the query come first`() {
        val users = MentionCompletion.suggestions("hi @an", members)
        assertEquals(listOf(MentionSuggestion.User(ann), MentionSuggestion.User(joanna)), users)
    }

    @Test
    fun `offers silent only at the start and room anywhere`() {
        assertEquals(listOf(MentionSuggestion.Silent), MentionCompletion.suggestions("@si", members))
        assertEquals(listOf(MentionSuggestion.Room), MentionCompletion.suggestions("hey @ro", members))
    }

    @Test
    fun `completing replaces the query with the name`() {
        assertEquals("hi @Ann Lee ", MentionCompletion.complete("hi @an", MentionSuggestion.User(ann)))
        assertEquals("@silent ", MentionCompletion.complete("@s", MentionSuggestion.Silent))
    }
}
