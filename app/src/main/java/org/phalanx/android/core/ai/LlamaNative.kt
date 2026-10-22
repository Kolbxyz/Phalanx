package org.phalanx.android.core.ai

/** Receives the model's reply as it is written; return false to stop it. */
internal fun interface TokenSink {
    fun onToken(bytes: ByteArray): Boolean
}

/**
 * The JNI side of llama.cpp (`src/main/cpp/phalanx_llm.cpp`). Text crosses as UTF-8 bytes. A handle
 * is one loaded model; use it from one thread at a time and [free] it once. Failures throw
 * [IllegalStateException].
 */
internal object LlamaNative {
    /** False on a 32-bit phone, where the library is not shipped. */
    val isAvailable: Boolean = try {
        System.loadLibrary("phalanx_llm")
        true
    } catch (_: UnsatisfiedLinkError) {
        false
    }

    external fun load(path: String, threads: Int): Long

    /** Stops the [complete] running on [handle], from any thread. */
    external fun cancel(handle: Long)

    external fun free(handle: Long)

    /** True if the reply ran to its end, false if it was cancelled. */
    external fun complete(
        handle: Long,
        system: ByteArray,
        user: ByteArray,
        contextSize: Int,
        maxNewTokens: Int,
        sink: TokenSink
    ): Boolean
}
