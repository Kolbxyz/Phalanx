package org.phalanx.android.core.ai.translation

/** One direction of translation, between two language codes such as "fr" and "en". */
data class LanguagePair(val from: String, val to: String)

/** What a file is to its language pair. A pair has one vocabulary for both sides, or one each. */
internal enum class PairFileRole { Model, Shortlist, Vocabulary, SourceVocabulary, TargetVocabulary }

/**
 * A translation model file this build accepts, identified by the SHA-256 of its contents, as
 * Mozilla publishes them for Firefox's translations (firefox-translations-models), unpacked.
 * [name] is the file as published; [maxBytes] is at least its size.
 */
internal data class KnownPairFile(
    val name: String,
    val maxBytes: Long,
    val sha256: String,
    val pair: LanguagePair,
    val role: PairFileRole
)

/**
 * The files accepted, by pair. Only these exact files load, so a swapped or corrupted model never
 * reaches the native parser. Compare a hash with Mozilla's before adding one.
 */
internal val KNOWN_PAIR_FILES = listOf<KnownPairFile>()

/** The paths a loaded pair needs; a pair with one vocabulary passes it for both sides. */
internal class PairFiles(val model: String, val shortlist: String, val sourceVocab: String, val targetVocab: String)

/** [files] as a complete pair, or null when one is missing. */
internal fun pairFilesOf(files: Map<PairFileRole, String>): PairFiles? {
    val source = files[PairFileRole.SourceVocabulary] ?: files[PairFileRole.Vocabulary]
    val target = files[PairFileRole.TargetVocabulary] ?: files[PairFileRole.Vocabulary]
    val vocabs = if (source != null && target != null) source to target else null
    return vocabs?.let { (from, to) ->
        val model = files[PairFileRole.Model]
        val shortlist = files[PairFileRole.Shortlist]
        if (model != null && shortlist != null) PairFiles(model, shortlist, from, to) else null
    }
}
