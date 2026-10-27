package org.phalanx.android.core.ai.translation

/** One direction of translation, between two language codes such as "fr" and "en". */
data class LanguagePair(val from: String, val to: String)

/** What a file is to its language pair. A pair has one vocabulary for both sides, or one each. */
internal enum class PairFileRole { Model, Shortlist, Vocabulary, SourceVocabulary, TargetVocabulary }

/**
 * A translation model file this build accepts, identified by the SHA-256 of its contents, as
 * Mozilla publishes them for Firefox's translations. [name] is the file as Firefox names it, at
 * [url]; [maxBytes] is its size. The two directions of a pair may share a file, such as their
 * vocabulary: it is then listed under both, with the same hash.
 */
internal data class KnownPairFile(
    val name: String,
    val url: String,
    val maxBytes: Long,
    val sha256: String,
    val pair: LanguagePair,
    val role: PairFileRole
)

/**
 * The files accepted, by pair: the ones Firefox's release channel translates with (Remote Settings,
 * collection "translations-models"). Only these exact files load, so a swapped or corrupted model
 * never reaches the native parser. Compare a hash with that collection's before adding one.
 */
internal val KNOWN_PAIR_FILES = listOf(
    pairFile(
        "deen", "model.deen.intgemm.alphas.bin", 31_561_787L,
        "3e6f7c2c2425d10824797270b382bee718ff34af2cab9308841c82ca46dc6f20",
        "f44b1b1b-9df6-4ece-971e-0e5ce96fae54.bin"
    ),
    pairFile(
        "deen", "lex.50.50.deen.s2t.bin", 4_945_796L,
        "113b98460468360cca68c042e1cddf49c4e1931cbb975ed04349c9a3bd607010",
        "d0e4efcb-6145-43db-a69e-568904cc2925.bin"
    ),
    pairFile(
        "deen", "vocab.deen.spm", 810_073L,
        "69f730becafa48e3bb2c244eab66456877c08959a02f2bd5519b5a3088b62f9c",
        "8ad4d93e-21e6-4862-81d5-c1c3a7d0767b.spm"
    ),
    pairFile(
        "ende", "model.ende.intgemm.alphas.bin", 31_561_787L,
        "8df29d9494d19f47fd5d97c6a73474c6f657e9f81c1a607c431d02befdf3810f",
        "23db71e7-b6d9-45eb-a47d-0290d7d8ef63.bin"
    ),
    pairFile(
        "ende", "lex.50.50.ende.s2t.bin", 4_347_672L,
        "7ed39f1cffbd68a27ddf05bbfe068de2060f1d7e69f1a20e27ae923551dd7393",
        "bc072b1a-7749-43f7-9fe0-34a6dff10c4a.bin"
    ),
    pairFile(
        "ende", "vocab.ende.spm", 810_073L,
        "69f730becafa48e3bb2c244eab66456877c08959a02f2bd5519b5a3088b62f9c",
        "261225ea-5a52-455b-981c-7d09c6e6da3c.spm"
    ),
    pairFile(
        "enes", "model.enes.intgemm.alphas.bin", 31_561_787L,
        "3b1c399511c01c84c36fae5c0524df44096288efdc8236e182b5c97d7ad2244c",
        "a4ba0e94-16de-4058-9a44-5bbbbb3c8640.bin"
    ),
    pairFile(
        "enes", "lex.50.50.enes.s2t.bin", 4_198_436L,
        "7d51237c0a07027dcd61643cfbbb0f8c48597d19907ef53d2cae9d6bec2cf25c",
        "1834a61e-0331-4c4a-bbc0-dda02afa8188.bin"
    ),
    pairFile(
        "enes", "vocab.enes.spm", 816_054L,
        "5ae254fa9b15aa182e70fd2a6186b1333c63a29a48043a9224c6aa4fcac058ad",
        "170634fd-511a-4a28-b723-0a1025c67feb.spm"
    ),
    pairFile(
        "enfr", "model.enfr.intgemm.alphas.bin", 31_561_787L,
        "6322e296d4fecfe395a8d5723da4ec37ecbe6d7613bb1dfcf4b28e2a47498b68",
        "e0d7f3ee-163a-4b0a-8e44-8b208b39477d.bin"
    ),
    pairFile(
        "enfr", "lex.50.50.enfr.s2t.bin", 4_372_936L,
        "2585ed98d3af0bc949865aedeb390493d591f56870814376e73e4144c41ed059",
        "293d1823-ff05-48c9-837b-cff8fd33f197.bin"
    ),
    pairFile(
        "enfr", "vocab.enfr.spm", 814_404L,
        "783abf3abe075afdf8d85d233994bef2c3a064e935ab1bed946820aff6ac002a",
        "c7c1bcf3-71f6-425a-a6ed-403bd9a0a759.spm"
    ),
    pairFile(
        "enit", "model.enit.intgemm.alphas.bin", 31_561_787L,
        "248f47568788ecc351da7e5e07064d4153b4f71e011364ae2c931ffeec4d1cc2",
        "29ae0d70-5e37-49e8-8da6-3e2c58da61d1.bin"
    ),
    pairFile(
        "enit", "lex.50.50.enit.s2t.bin", 4_133_192L,
        "8b21914804625b2777dae7fdb636eb78f02a3eb8b7bceaa50f29ac740961da93",
        "3730ff60-2291-4c87-888d-2dc4593b20db.bin"
    ),
    pairFile(
        "enit", "vocab.enit.spm", 812_724L,
        "3ef0211d4ae6db21440892f180f2019fe2bfc110a330ffa9d2eca9665e4f2bc5",
        "d9d5ffa9-b919-4491-9ab6-3dec34459768.spm"
    ),
    pairFile(
        "esen", "model.esen.intgemm.alphas.bin", 31_561_787L,
        "4aed7734152ae0045d1a69ae49c86cfda18f53c61f90e95e1d1de1c7c7c3b033",
        "d7256ea9-7731-4f15-866c-62998eef93b9.bin"
    ),
    pairFile(
        "esen", "lex.50.50.esen.s2t.bin", 4_636_248L,
        "e2610211d3b9577d012638fe7e7e74ed7b4b708ce96b9e792e67c282a6492daa",
        "a55a9cf2-8a8c-4942-a528-91e93d081746.bin"
    ),
    pairFile(
        "esen", "vocab.esen.spm", 816_054L,
        "5ae254fa9b15aa182e70fd2a6186b1333c63a29a48043a9224c6aa4fcac058ad",
        "8695cc3d-7897-4c29-808b-bc983a93d1cd.spm"
    ),
    pairFile(
        "fren", "model.fren.intgemm.alphas.bin", 31_561_787L,
        "15f997bc0d13808b0b0fbd0786e684a3c8a52adcd8071844b76123fdacbf2b90",
        "8ef78b1c-df6c-49fc-8cae-cf8c0c2a6630.bin"
    ),
    pairFile(
        "fren", "lex.50.50.fren.s2t.bin", 4_824_120L,
        "87c6752ea908f5f0347c10ac0cf7d80d9c2f4f20c81c90168f3e8230b56d4440",
        "c4d99496-81bf-480f-b0bb-3aebacdcbe8e.bin"
    ),
    pairFile(
        "fren", "vocab.fren.spm", 814_404L,
        "783abf3abe075afdf8d85d233994bef2c3a064e935ab1bed946820aff6ac002a",
        "11ecb9da-60dd-4b39-aa12-27883099294b.spm"
    ),
    pairFile(
        "iten", "model.iten.intgemm.alphas.bin", 31_561_787L,
        "21b70978ce2f3b4da7a06b5de86a09abe3acd30b9eee1b2ebb3582b9bad790bf",
        "a6e3de84-6612-4546-a4ef-b658ee23ca08.bin"
    ),
    pairFile(
        "iten", "lex.50.50.iten.s2t.bin", 4_713_616L,
        "0700d6d70b30490e68ff9deca0b45b80310745b491855bde3a13e1692d0cbce1",
        "122ac310-0f7b-40d5-b49d-bcee81077c85.bin"
    ),
    pairFile(
        "iten", "vocab.iten.spm", 812_724L,
        "3ef0211d4ae6db21440892f180f2019fe2bfc110a330ffa9d2eca9665e4f2bc5",
        "51b5a437-9daa-481e-a9bc-311f2b3f490c.spm"
    )
)

/** [directory] is Mozilla's name for the pair, such as "enfr" for English into French. */
private fun pairFile(directory: String, name: String, size: Long, sha256: String, attachment: String) =
    KnownPairFile(
        name = name,
        url = "$MOZILLA_ATTACHMENTS/$attachment",
        maxBytes = size,
        sha256 = sha256,
        pair = LanguagePair(directory.take(2), directory.drop(2)),
        role = when (name.substringBefore('.')) {
            "model" -> PairFileRole.Model
            "lex" -> PairFileRole.Shortlist
            else -> PairFileRole.Vocabulary
        }
    )

// Firefox's own download location for them, Mozilla's CDN, over HTTPS.
private const val MOZILLA_ATTACHMENTS =
    "https://firefox-settings-attachments.cdn.mozilla.net/main-workspace/translations-models"

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
