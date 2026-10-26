package org.phalanx.android.core.ai.speech

/** Receives the transcript as whisper.cpp writes it, one segment at a time. */
internal fun interface SegmentSink {
    fun onSegment(bytes: ByteArray)
}

/**
 * The JNI side of whisper.cpp (`src/main/cpp/phalanx_stt.cpp`). Audio crosses as 16 kHz mono
 * floats, text as UTF-8 bytes. A handle is one loaded model; use it from one thread at a time and
 * [free] it once. Failures throw [IllegalStateException].
 */
internal object WhisperNative {
    /** False on a 32-bit phone, where the library is not shipped. */
    val isAvailable: Boolean = try {
        System.loadLibrary("phalanx_stt")
        loadBackend()
    } catch (_: UnsatisfiedLinkError) {
        false
    }

    /** Loads the fastest ggml CPU backend this processor runs, shared with the language model. */
    private external fun loadBackend(): Boolean

    external fun load(path: String, threads: Int): Long

    /** Stops the [transcribe] running on [handle], from any thread. */
    external fun cancel(handle: Long)

    external fun free(handle: Long)

    /**
     * Writes out [samples], 16 kHz mono, in [language] ("fr", "en", ... or "auto" to detect it).
     * Returns the code of the language heard, or null when it was cancelled.
     */
    external fun transcribe(handle: Long, samples: FloatArray, language: String, sink: SegmentSink): String?
}
