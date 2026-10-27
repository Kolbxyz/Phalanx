package org.phalanx.android.core.ai

/**
 * A model file this build accepts, identified by the SHA-256 of its contents. [name] is the file as
 * its publisher names it, and [url] downloads it from them. [maxBytes] is its exact size: a copy is
 * stopped past the largest one, so a wrong file never fills the storage, and a language model
 * imported by earlier builds is recognised by it.
 */
data class KnownModel(val name: String, val url: String, val maxBytes: Long, val sha256: String)

/**
 * The language models: Ministral 3 3B Instruct (Apache 2.0), from
 * huggingface.co/mistralai/Ministral-3-3B-Instruct-2512-GGUF.
 * Only these exact files load, so a swapped or corrupted model never reaches the native parser.
 * Compare a hash with the one on that page before adding a model.
 */
internal val KNOWN_MODELS = listOf(
    KnownModel(
        "Ministral-3-3B-Instruct-2512-Q4_K_M.gguf",
        "$MINISTRAL/Ministral-3-3B-Instruct-2512-Q4_K_M.gguf",
        2_147_023_008L,
        "9ed150d4367e68df0ac8e1540f6ddc65b42d0ee26378329d1ecbca60f93fc5f8"
    ),
    KnownModel(
        "Ministral-3-3B-Instruct-2512-Q5_K_M.gguf",
        "$MINISTRAL/Ministral-3-3B-Instruct-2512-Q5_K_M.gguf",
        2_474_178_720L,
        "e23dd88b0e0951d3f5784d6d4092210cda2b006b251f33b226b283a6c9d1f6bb"
    )
)

/** Mistral's language model files on Hugging Face. */
internal const val MINISTRAL = "https://huggingface.co/mistralai/Ministral-3-3B-Instruct-2512-GGUF/resolve/main"
