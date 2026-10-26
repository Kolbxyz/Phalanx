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
        loadBackend()
    } catch (_: UnsatisfiedLinkError) {
        false
    }

    /** Loads the fastest llama.cpp CPU backend this processor runs; false if none does. */
    private external fun loadBackend(): Boolean

    /** [threads] write the reply, [promptThreads] read the prompt. */
    external fun load(path: String, threads: Int, promptThreads: Int): Long

    /** Stops the [complete] or [describe] running on [handle], from any thread. */
    external fun cancel(handle: Long)

    external fun free(handle: Long)

    /**
     * True if the reply ran to its end, false if it was cancelled. [grammar], GBNF with a "root"
     * rule, holds the reply to that shape; null leaves it free.
     */
    external fun complete(
        handle: Long,
        system: ByteArray,
        user: ByteArray,
        contextSize: Int,
        maxNewTokens: Int,
        grammar: ByteArray?,
        sink: TokenSink
    ): Boolean

    /**
     * Writes about an image, [width] by [height] RGB bytes in [rgb], answering [user] and held to
     * [grammar] as in [complete]; [projector] is the vision model file, loaded with the first image.
     * True if the reply ran to its end.
     */
    external fun describe(
        handle: Long,
        projector: String,
        rgb: ByteArray,
        width: Int,
        height: Int,
        system: ByteArray,
        user: ByteArray,
        contextSize: Int,
        maxNewTokens: Int,
        grammar: ByteArray?,
        sink: TokenSink
    ): Boolean
}
