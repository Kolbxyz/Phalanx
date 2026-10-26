package org.phalanx.android.core.ai.photos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phalanx.android.core.ai.vision.wordsOf

class PhotoNoteTest {
    private val note = PhotoNote(
        roomId = "!r",
        description = "Une facture EDF posée sur une table de cuisine.",
        text = "EDF Total à payer : 84,20 €",
        sentAtMillis = 0,
        thumbnail = "mxc",
        mimeType = "image/jpeg"
    )

    @Test
    fun `every word must be found, whatever the case or accents`() {
        assertTrue(note.matches("facture edf"))
        assertTrue(note.matches("Posee CUISINE"))
        assertTrue(note.matches("total a payer"))
        assertFalse(note.matches("facture gaz"))
    }

    @Test
    fun `a blank query finds nothing`() {
        assertFalse(note.matches("   "))
    }

    @Test
    fun `the model's reply is read, and anything else refused`() {
        assertEquals("A cat.", wordsOf("""{"description": "A cat.", "text": ""}""")?.description)
        assertNull(wordsOf("""{"description": "A cat"""))
    }
}
