package org.phalanx.android.ui.screens.room

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.phalanx.android.data.model.Member

class MentionCompletionTest {
    private val ann = Member("@ann:s.org", "Ann Lee", null)
    private val joanna = Member("@discord_9:s.org", "Joanna", null)
    private val members = listOf(joanna, ann)

    /** [text] with the cursor where "|" is, or at the end without one. */
    private fun input(text: String): TextFieldValue {
        val cursor = text.indexOf('|').takeIf { it >= 0 } ?: text.length
        return TextFieldValue(text.replace("|", ""), TextRange(cursor))
    }

    @Test
    fun `completes an at sign starting a word under the cursor`() {
        assertEquals("an", MentionCompletion.query(input("hi @an")))
        assertEquals("an", MentionCompletion.query(input("hi @an| how are you")))
        assertEquals("a", MentionCompletion.query(input("hi @a|n there")))
        assertNull(MentionCompletion.query(input("mail me@an")))
        assertNull(MentionCompletion.query(input("hi @ann |done")))
    }

    @Test
    fun `nothing completes over a selection`() {
        assertNull(MentionCompletion.query(TextFieldValue("hi @an", TextRange(3, 6))))
    }

    @Test
    fun `names starting with the query come first`() {
        val users = MentionCompletion.suggestions(input("hi @an"), members)
        assertEquals(listOf(MentionSuggestion.User(ann), MentionSuggestion.User(joanna)), users)
    }

    @Test
    fun `offers silent only at the start and room anywhere`() {
        assertEquals(listOf(MentionSuggestion.Silent), MentionCompletion.suggestions(input("@si"), members))
        assertEquals(listOf(MentionSuggestion.Silent), MentionCompletion.suggestions(input("@si| hello"), members))
        assertEquals(listOf(MentionSuggestion.Room), MentionCompletion.suggestions(input("hey @ro| look"), members))
    }

    @Test
    fun `completing at the end adds a space`() {
        val done = MentionCompletion.complete(input("hi @an"), MentionSuggestion.User(ann))
        assertEquals(input("hi @Ann Lee |"), done)
    }

    @Test
    fun `completing mid-text replaces the whole word and keeps the rest`() {
        val done = MentionCompletion.complete(input("hi @a|nn how are you"), MentionSuggestion.User(ann))
        assertEquals(input("hi @Ann Lee |how are you"), done)
        val tight = MentionCompletion.complete(input("@s|,x"), MentionSuggestion.Silent)
        assertEquals(input("@silent |,x"), tight)
    }
}
