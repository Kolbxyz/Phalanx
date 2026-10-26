package org.phalanx.android.core.ai.speech

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ResampleTest {
    @Test
    fun `16 kHz audio is left as it is`() {
        val samples = floatArrayOf(0.1f, 0.2f, 0.3f)
        assertArrayEquals(samples, resample(samples, WHISPER_RATE), 0f)
    }

    @Test
    fun `48 kHz audio keeps one sample in three`() {
        val samples = FloatArray(48_000) { it.toFloat() }
        val resampled = resample(samples, 48_000)
        assertEquals(16_000, resampled.size)
        assertEquals(3f, resampled[1], 0f)
    }

    @Test
    fun `8 kHz audio is interpolated between samples`() =
        assertArrayEquals(floatArrayOf(0f, 0.5f, 1f, 1f), resample(floatArrayOf(0f, 1f), 8_000), 1e-6f)
}
