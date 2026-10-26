package org.phalanx.android.core.media

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.phalanx.android.data.model.KitchenMix

/**
 * Emoji Kitchen: which pairs of emoji mix, from an index bundled with the app (see
 * tools/emoji-kitchen), and their images, fetched from Google's servers only when shown and kept in
 * the cache. The index never leaves the phone; only the images asked for are requested.
 */
@Singleton
class EmojiKitchen @Inject constructor(@ApplicationContext private val context: Context) {
    private val lock = Mutex()
    private var index: Index? = null
    private val dir get() = File(context.cacheDir, "kitchen").apply { mkdirs() }

    /** Every emoji that mixes with at least one other, in codepoint order. */
    suspend fun bases(): List<String> = load().byEmoji.keys.sorted()

    /** The mixes [codepoints] is part of, itself with itself first. */
    suspend fun mixesOf(codepoints: String): List<KitchenMix> = load().byEmoji[codepoints].orEmpty()
        .sortedByDescending { it.left == it.right }

    /**
     * The mix of two emoji as typed, or null when they don't mix. Keyboards differ in whether they
     * add variation selectors, so those are ignored on both sides.
     */
    suspend fun mixOf(first: String, second: String): KitchenMix? {
        val loaded = load()
        val a = loaded.byBare[bareOf(codepointsOf(first))] ?: return null
        val b = bareOf(codepointsOf(second))
        return loaded.byEmoji[a]?.firstOrNull { bareOf(if (bareOf(it.left) == bareOf(a)) it.right else it.left) == b }
    }

    /** The mixes one emoji as typed is part of, as [mixesOf] orders them; empty when it mixes with none. */
    suspend fun mixesOfTyped(emoji: String): List<KitchenMix> =
        load().byBare[bareOf(codepointsOf(emoji))]?.let { mixesOf(it) }.orEmpty()

    /**
     * The bases whose Unicode name has a word starting with each word of [query], so "cat" finds
     * 🐱 and 😺. Names are in English, as Unicode gives them; a blank query gives every base.
     */
    suspend fun search(query: String): List<String> {
        val words = query.lowercase().split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return bases()
        return withContext(Dispatchers.Default) {
            bases().filter { base ->
                val name = nameWords(base)
                words.all { word -> name.any { it.startsWith(word) } }
            }
        }
    }

    /** The mix's PNG, from the cache or downloaded once. */
    suspend fun image(mix: KitchenMix): Result<File> = withContext(Dispatchers.IO) {
        val file = File(dir, "${mix.left}_${mix.right}.png")
        if (file.length() > 0) return@withContext Result.success(file)
        val connection = URL(urlOf(mix)).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.instanceFollowRedirects = false
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(IOException("Emoji Kitchen answered ${connection.responseCode}"))
            }
            if (connection.contentLengthLong > MAX_BYTES) return@withContext Result.failure(IOException("Too large"))
            // Capped while reading too: a response without a length would otherwise be read whole.
            val bytes = connection.inputStream.use { it.readAtMost(MAX_BYTES) }
                ?: return@withContext Result.failure(IOException("Too large"))
            // Written whole, then renamed, so a cut download never passes for a cached image.
            val part = File(dir, file.name + ".part")
            part.writeBytes(bytes)
            if (!part.renameTo(file)) {
                part.delete()
                return@withContext Result.failure(IOException("Could not keep the image"))
            }
            Result.success(file)
        } catch (e: IOException) {
            Result.failure(e)
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun load(): Index = lock.withLock {
        index ?: withContext(Dispatchers.IO) { parse() }.let { byEmoji ->
            Index(byEmoji, byEmoji.keys.associateBy(::bareOf))
        }.also { index = it }
    }

    /** [byBare] finds an index key from its codepoints without variation selectors. */
    private class Index(val byEmoji: Map<String, List<KitchenMix>>, val byBare: Map<String, String>)

    private fun parse(): Map<String, List<KitchenMix>> {
        val byEmoji = HashMap<String, MutableList<KitchenMix>>()
        context.assets.open(ASSET).bufferedReader().useLines { lines ->
            var dates = emptyList<String>()
            lines.forEach { line ->
                if (line.startsWith("D ")) {
                    dates = line.substring(2).split(' ')
                    return@forEach
                }
                val (a, b, date) = line.split(' ')
                val swapped = date.endsWith('!')
                val mix = KitchenMix(
                    left = if (swapped) b else a,
                    right = if (swapped) a else b,
                    date = dates[date.trimEnd('!').toInt()]
                )
                byEmoji.getOrPut(a) { mutableListOf() }.add(mix)
                if (a != b) byEmoji.getOrPut(b) { mutableListOf() }.add(mix)
            }
        }
        return byEmoji
    }

    private companion object {
        const val ASSET = "emoji_kitchen.txt"
        const val TIMEOUT_MILLIS = 15_000
        const val MAX_BYTES = 2L * 1024 * 1024
    }
}

private const val BASE_URL = "https://www.gstatic.com/android/keyboard/emojikitchen"
private const val BUFFER_BYTES = 16 * 1024
private val VARIATION_SELECTOR = Regex("-?fe0f")

private fun urlOf(mix: KitchenMix): String {
    val left = mix.left.split('-').joinToString("-") { "u$it" }
    val right = mix.right.split('-').joinToString("-") { "u$it" }
    return "$BASE_URL/${mix.date}/$left/${left}_$right.png"
}

/** "1f469-200d-1f4bb" for the emoji as typed. */
private fun codepointsOf(emoji: String): String =
    emoji.codePoints().toArray().joinToString("-") { Integer.toHexString(it) }

/** [codepoints] without variation selectors, which keyboards add or leave out. */
private fun bareOf(codepoints: String): String = codepoints.replace(VARIATION_SELECTOR, "")

/** The lowercase words of the Unicode names of [codepoints], as "smiling", "cat", "face". */
private fun nameWords(codepoints: String): List<String> = codepoints.split('-')
    .mapNotNull { Character.getName(it.toInt(radix = 16)) }
    .flatMap { it.lowercase().split(' ', '-') }

/** Everything left in the stream, or null if it holds more than [limit] bytes. */
private fun InputStream.readAtMost(limit: Long): ByteArray? {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(BUFFER_BYTES)
    while (true) {
        val read = read(buffer)
        if (read < 0) return out.toByteArray()
        if (out.size() + read > limit) return null
        out.write(buffer, 0, read)
    }
}
