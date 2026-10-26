package org.phalanx.android.core.ai.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairFilesTest {
    @Test
    fun `one vocabulary serves both sides`() {
        val files = pairFilesOf(
            mapOf(PairFileRole.Model to "m", PairFileRole.Shortlist to "s", PairFileRole.Vocabulary to "v")
        )
        assertEquals("v", files?.sourceVocab)
        assertEquals("v", files?.targetVocab)
    }

    @Test
    fun `separate vocabularies are kept apart`() {
        val files = pairFilesOf(
            mapOf(
                PairFileRole.Model to "m",
                PairFileRole.Shortlist to "s",
                PairFileRole.SourceVocabulary to "src",
                PairFileRole.TargetVocabulary to "trg"
            )
        )
        assertEquals("src", files?.sourceVocab)
        assertEquals("trg", files?.targetVocab)
    }

    @Test
    fun `a pair missing a file is not complete`() {
        assertNull(pairFilesOf(mapOf(PairFileRole.Model to "m", PairFileRole.Vocabulary to "v")))
    }
}
