package org.phalanx.android.data.model

import kotlinx.serialization.Serializable

/**
 * One Emoji Kitchen image: two emoji, each a codepoint sequence like "1f469-200d-1f4bb", in the
 * order the image is stored under, and the release [date] that names its folder.
 */
@Serializable
data class KitchenMix(val left: String, val right: String, val date: String) {
    /** What the mix reads as where images are not shown: the two emoji side by side. */
    val body: String get() = emojiOf(left) + emojiOf(right)
}

/** "1f469-200d-1f4bb" as the emoji it encodes. */
fun emojiOf(codepoints: String): String = buildString {
    codepoints.split('-').forEach { appendCodePoint(it.toInt(radix = 16)) }
}

/** The mixes the user sent lately, newest first, and the ones they starred. */
@Serializable
data class KitchenPicks(val recent: List<KitchenMix> = emptyList(), val favourites: List<KitchenMix> = emptyList())
