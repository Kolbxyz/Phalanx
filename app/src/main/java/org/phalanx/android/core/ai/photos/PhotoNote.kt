package org.phalanx.android.core.ai.photos

import java.text.Normalizer
import kotlinx.serialization.Serializable

/**
 * A photo as words, for finding it by search: what it shows and the text written in it. [thumbnail]
 * is the photo's serialized media reference, so a hit can show it.
 */
@Serializable
data class PhotoNote(
    val roomId: String,
    val description: String,
    val text: String,
    val sentAtMillis: Long,
    val thumbnail: String,
    val mimeType: String?
)

/** Whether every word of [query] is in [note], whatever the case or accents. */
internal fun PhotoNote.matches(query: String): Boolean {
    val words = plain(query).split(' ').filter { it.isNotEmpty() }
    if (words.isEmpty()) return false
    val haystack = plain("$description $text")
    return words.all { it in haystack }
}

private fun plain(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(ACCENTS, "").lowercase().replace(SPACES, " ")

private val ACCENTS = Regex("\\p{Mn}+")
private val SPACES = Regex("\\s+")
