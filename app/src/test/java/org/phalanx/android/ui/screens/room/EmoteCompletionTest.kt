package org.phalanx.android.ui.screens.room

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.phalanx.android.data.model.CustomEmote
import org.phalanx.android.data.model.MediaRef

class EmoteCompletionTest {
    private val blobcat = CustomEmote("blobcat", "mxc://s.org/a", MediaRef("a"))
    private val catjam = CustomEmote("catjam", "mxc://s.org/b", MediaRef("b"))
    private val emotes = listOf(catjam, blobcat)

    /** [text] with the cursor where "|" is, or at the end without one. */
    private fun input(text: String): TextFieldValue {
        val cursor = text.indexOf('|').takeIf { it >= 0 } ?: text.length
        return TextFieldValue(text.replace("|", ""), TextRange(cursor))
    }

    @Test
    fun `completes a colon and two characters starting a word`() {
        assertEquals("ca", EmoteCompletion.query(input("hi :ca")))
        assertNull(EmoteCompletion.query(input("hi :c")))
        assertNull(EmoteCompletion.query(input("at 10:30")))
        assertNull(EmoteCompletion.query(input("note:ab")))
    }

    @Test
    fun `shortcodes starting with the query come first`() {
        assertEquals(listOf(catjam, blobcat), EmoteCompletion.suggestions(input(":cat"), emotes))
        assertEquals(listOf(blobcat), EmoteCompletion.suggestions(input(":BLOB"), emotes))
    }

    @Test
    fun `completing replaces the word and its closing colon`() {
        assertEquals(TextFieldValue("hi :blobcat: ", TextRange(13)), EmoteCompletion.complete(input("hi :bl"), blobcat))
        assertEquals(
            TextFieldValue("hi :blobcat: there", TextRange(13)),
            EmoteCompletion.complete(input("hi :bl|: there"), blobcat)
        )
    }
}
