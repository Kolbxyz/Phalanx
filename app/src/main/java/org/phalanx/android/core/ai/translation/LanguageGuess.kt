package org.phalanx.android.core.ai.translation

/**
 * Whether [text] reads as [language] rather than [other], from the short words every sentence is
 * full of. Done on the phone in a microsecond, where the system's language detector would be a
 * Google service on Pixel phones. False when neither is clearly ahead, or for a language not
 * listed here: a message is then left as it is rather than translated wrongly.
 */
internal fun readsAs(text: String, language: String, other: String): Boolean {
    val mine = COMMON_WORDS[language].orEmpty()
    val theirs = COMMON_WORDS[other].orEmpty()
    val words = WORD.findAll(text.lowercase()).map { it.value }.toList()
    val hits = words.count { it in mine && it !in theirs }
    val against = words.count { it in theirs && it !in mine }
    // An unknown [other] has no words, so its absence never counts as a win.
    return theirs.isNotEmpty() && hits >= MIN_HITS && hits > against
}

private val WORD = Regex("\\p{L}+(?:'\\p{L}+)?")
private const val MIN_HITS = 1

private val COMMON_WORDS: Map<String, Set<String>> = mapOf(
    "en" to setOf(
        "the", "and", "is", "are", "you", "i", "it", "that", "this", "with", "for", "have", "was", "what",
        "my", "your", "not", "but", "be", "of", "to", "we", "they", "do", "don't", "i'm", "it's", "can",
        "will", "just", "so", "how", "there", "here", "yes", "no", "thanks", "hello", "hi", "would"
    ),
    "fr" to setOf(
        "le", "la", "les", "et", "est", "je", "tu", "il", "elle", "nous", "vous", "ils", "un", "une",
        "des", "du", "de", "que", "qui", "pas", "pour", "avec", "sur", "dans", "ce", "c'est", "j'ai",
        "mais", "oui", "non", "merci", "bonjour", "salut", "très", "aussi", "être", "avoir", "fait", "ça"
    ),
    "de" to setOf(
        "der", "die", "das", "und", "ist", "ich", "du", "er", "sie", "wir", "ihr", "ein", "eine", "nicht",
        "mit", "auf", "für", "von", "zu", "den", "dem", "auch", "aber", "ja", "nein", "danke", "hallo",
        "wie", "was", "bin", "hast", "habe", "noch", "schon", "sehr", "kann", "wird", "mir", "dich"
    ),
    "es" to setOf(
        "el", "la", "los", "las", "y", "es", "yo", "tú", "él", "ella", "nosotros", "un", "una", "que",
        "no", "por", "para", "con", "en", "de", "del", "lo", "pero", "sí", "gracias", "hola", "muy",
        "está", "estoy", "qué", "cómo", "también", "tengo", "eres", "soy", "mi", "tu", "hay", "ya"
    ),
    "it" to setOf(
        "il", "lo", "la", "gli", "le", "e", "è", "io", "tu", "lui", "lei", "noi", "voi", "un", "una",
        "che", "non", "per", "con", "di", "del", "della", "ma", "sì", "grazie", "ciao", "molto", "sono",
        "sei", "ho", "hai", "anche", "come", "cosa", "questo", "quello", "mi", "ti", "ci", "perché"
    )
)
