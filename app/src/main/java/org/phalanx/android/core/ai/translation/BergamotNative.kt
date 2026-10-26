package org.phalanx.android.core.ai.translation

/**
 * The JNI side of Bergamot (`src/main/cpp/phalanx_mt.cpp`). A handle is one loaded language pair;
 * use it from one thread at a time and [free] it once. Failures throw [IllegalStateException].
 */
internal object BergamotNative {
    /** False on a 32-bit phone, where the library is not shipped. */
    val isAvailable: Boolean = try {
        System.loadLibrary("phalanx_mt")
        true
    } catch (_: UnsatisfiedLinkError) {
        false
    }

    external fun load(model: String, shortlist: String, sourceVocab: String, targetVocab: String, threads: Int): Long

    /** [text] as UTF-8, translated whole; Bergamot splits it into sentences itself. */
    external fun translate(handle: Long, text: ByteArray): ByteArray

    external fun free(handle: Long)
}
