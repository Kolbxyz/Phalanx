package org.phalanx.android.core.matrix

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.phalanx.android.core.security.SecretBox

/**
 * The custom emoji seen in messages, by scope: an account and a network ("Discord") or Matrix
 * itself. No network lists the emoji a user may send, so what [RoomEmotes] saw is what it offers.
 * On this phone only, sealed with the Keystore key like the Emoji Kitchen picks.
 */
@Singleton
internal class SeenEmoteStore @Inject constructor(
    @ApplicationContext context: Context,
    private val box: SecretBox
) {
    @Serializable
    data class Seen(val scope: String, val shortcode: String, val url: String, val roomId: String)

    private val file = File(context.filesDir, FILE_NAME)
    private val lock = Mutex()
    private var all: List<Seen>? = null

    /** [scope]'s emoji, newest first. */
    suspend fun of(scope: String): List<Seen> = lock.withLock { loadLocked().filter { it.scope == scope } }

    /** Adds the emoji not seen yet in [scope], true when there were any; nothing is written otherwise. */
    suspend fun add(scope: String, roomId: String, emotes: List<Pair<String, String>>): Result<Boolean> =
        lock.withLock {
            val current = loadLocked()
            val known = current.filter { it.scope == scope }
            val fresh = emotes.distinctBy { it.first }.filter { (shortcode, url) ->
                known.none { it.shortcode == shortcode || it.url == url }
            }.take(MAX_PER_SCOPE)
            if (fresh.isEmpty()) return@withLock Result.success(false)
            val added = fresh.map { (shortcode, url) -> Seen(scope, shortcode, url, roomId) }
            val next = added + known.take(MAX_PER_SCOPE - added.size) + current.filter { it.scope != scope }
            save(next).map {
                all = next
                true
            }
        }

    private suspend fun save(next: List<Seen>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val temp = File(file.parentFile, "$FILE_NAME.tmp")
            temp.writeBytes(box.seal(Json.encodeToString(next).toByteArray()))
            // Atomic on the same filesystem, so a crash never leaves half a file.
            if (!temp.renameTo(file)) throw IOException("Could not save seen emoji")
            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    private suspend fun loadLocked(): List<Seen> {
        all?.let { return it }
        val read = withContext(Dispatchers.IO) {
            if (!file.exists()) return@withContext emptyList()
            try {
                Json.decodeFromString<List<Seen>>(box.open(file.readBytes()).decodeToString())
            } catch (_: GeneralSecurityException) {
                // The key is gone (app data restored elsewhere): they will be seen again.
                emptyList()
            } catch (_: SerializationException) {
                emptyList()
            }
        }
        all = read
        return read
    }

    private companion object {
        const val FILE_NAME = "seen_emotes.bin"
        const val MAX_PER_SCOPE = 400
    }
}
