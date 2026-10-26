package org.phalanx.android.core.media

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.phalanx.android.data.model.KitchenMix
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Emoji Kitchen: which pairs of emoji mix, from an index bundled with the app (see
 * tools/emoji-kitchen), and their images, fetched from Google's servers only when shown and kept in
 * the cache. The index never leaves the phone; only the images asked for are requested.
 */
@Singleton
class EmojiKitchen @Inject constructor(@ApplicationContext private val context: Context) {
    private val lock = Mutex()
    private var index: Map<String, List<KitchenMix>>? = null
    private val dir get() = File(context.cacheDir, "kitchen").apply { mkdirs() }

    /** Every emoji that mixes with at least one other, in codepoint order. */
    suspend fun bases(): List<String> = load().keys.sorted()

    /** The mixes [codepoints] is part of, itself with itself first. */
    suspend fun mixesOf(codepoints: String): List<KitchenMix> = load()[codepoints].orEmpty()
        .sortedByDescending { it.left == it.right }

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
            val bytes = connection.inputStream.use { it.readBytes() }
            // Written whole, then renamed, so a cut download never passes for a cached image.
            val part = File(dir, file.name + ".part")
            part.writeBytes(bytes)
            part.renameTo(file)
            Result.success(file)
        } catch (e: IOException) {
            Result.failure(e)
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun load(): Map<String, List<KitchenMix>> = lock.withLock {
        index ?: withContext(Dispatchers.IO) { parse() }.also { index = it }
    }

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

    private fun urlOf(mix: KitchenMix): String {
        val left = mix.left.split('-').joinToString("-") { "u$it" }
        val right = mix.right.split('-').joinToString("-") { "u$it" }
        return "$BASE_URL/${mix.date}/$left/${left}_$right.png"
    }

    private companion object {
        const val ASSET = "emoji_kitchen.txt"
        const val BASE_URL = "https://www.gstatic.com/android/keyboard/emojikitchen"
        const val TIMEOUT_MILLIS = 15_000
        const val MAX_BYTES = 2L * 1024 * 1024
    }
}
