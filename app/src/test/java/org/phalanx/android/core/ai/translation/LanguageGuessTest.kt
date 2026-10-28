package org.phalanx.android.core.ai.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageGuessTest {
    @Test
    fun `an English message reads as English rather than French`() {
        assertTrue(readsAs("Are you coming to the party tonight?", "en", "fr"))
        assertFalse(readsAs("Are you coming to the party tonight?", "fr", "en"))
    }

    @Test
    fun `a French message is left alone by English to French`() {
        assertFalse(readsAs("Je ne sais pas si c'est possible demain", "en", "fr"))
    }

    @Test
    fun `words both languages share decide nothing`() {
        assertFalse(readsAs("la la la", "es", "it"))
    }

    @Test
    fun `no common word, or an unknown language, means no translation`() {
        assertFalse(readsAs("https://example.org 12:30", "en", "fr"))
        assertFalse(readsAs("Are you there?", "en", "nl"))
    }

    @Test
    fun `a pair round-trips through its settings key`() {
        assertEquals(LanguagePair("en", "fr"), LanguagePair.ofKey(LanguagePair("en", "fr").key))
        assertNull(LanguagePair.ofKey("enfr"))
        assertNull(LanguagePair.ofKey("en-"))
    }
}
