package org.phalanx.android.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phalanx.android.core.ai.speech.KNOWN_SPEECH_MODELS
import org.phalanx.android.core.ai.translation.KNOWN_PAIR_FILES
import org.phalanx.android.core.ai.translation.pairFilesOf

class ModelCatalogTest {
    private val sha256 = Regex("[0-9a-f]{64}")

    @Test
    fun `every model downloads over HTTPS and is pinned by a full hash`() {
        (KNOWN_MODELS + KNOWN_SPEECH_MODELS).forEach { model ->
            assertTrue(model.name, model.url.startsWith("https://") && model.url.endsWith("/${model.name}"))
            assertTrue(model.name, sha256.matches(model.sha256))
            assertTrue(model.name, model.maxBytes > 0)
        }
        KNOWN_PAIR_FILES.forEach { file ->
            assertTrue(file.name, file.url.startsWith("https://"))
            assertTrue(file.name, sha256.matches(file.sha256))
        }
    }

    @Test
    fun `every translation pair is complete`() {
        KNOWN_PAIR_FILES.groupBy { it.pair }.forEach { (pair, files) ->
            assertNotNull(pair.toString(), pairFilesOf(files.associate { it.role to it.name }))
        }
    }

    @Test
    fun `a file two pairs share is the same file in both`() {
        KNOWN_PAIR_FILES.groupBy { it.sha256 }.values.forEach { same ->
            assertEquals(same.first().name, 1, same.map { it.maxBytes }.distinct().size)
        }
    }
}
