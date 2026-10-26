package org.phalanx.android.core.ai

import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.phalanx.android.core.security.SecretBox

@Serializable
internal data class ShelvedResult<T>(val key: String, val savedAtMillis: Long, val value: T)

/**
 * One kind of AI result (transcripts, say) kept on this phone, sealed with the Keystore key: they
 * are room content, which never sits on disk in the clear. At most [capacity] of them, the oldest
 * dropped first, and none older than [maxAgeMillis]. Read once, then kept in memory.
 */
internal class AiShelf<T>(
    private val file: File,
    private val box: SecretBox,
    valueSerializer: KSerializer<T>,
    private val capacity: Int,
    private val maxAgeMillis: Long
) {
    private val serializer = ListSerializer(ShelvedResult.serializer(valueSerializer))
    private val lock = Mutex()
    private var loaded = false

    // By key, oldest first, guarded by [lock].
    private val results = LinkedHashMap<String, ShelvedResult<T>>()

    suspend fun get(key: String, now: Long = System.currentTimeMillis()): T? = lock.withLock {
        loadLocked()
        results[key]?.takeIf { now - it.savedAtMillis < maxAgeMillis }?.value
    }

    suspend fun put(key: String, value: T, now: Long = System.currentTimeMillis()) = lock.withLock {
        loadLocked()
        results.remove(key)
        results[key] = ShelvedResult(key, now, value)
        results.values.removeAll { now - it.savedAtMillis >= maxAgeMillis }
        while (results.size > capacity) results.remove(results.keys.first())
        try {
            writeLocked()
        } catch (_: IOException) {
            // A full disk, say: the result is still in memory and the next one retries.
        }
    }

    /** Every result still fresh, by key, oldest first. */
    suspend fun all(now: Long = System.currentTimeMillis()): Map<String, T> = lock.withLock {
        loadLocked()
        results.filterValues { now - it.savedAtMillis < maxAgeMillis }.mapValues { it.value.value }
    }

    /** Forgets every result and deletes the file. */
    suspend fun clear() = lock.withLock {
        results.clear()
        loaded = true
        withContext(Dispatchers.IO) { file.delete() }
        Unit
    }

    private suspend fun loadLocked() {
        if (loaded) return
        val read = withContext(Dispatchers.IO) {
            if (!file.exists()) return@withContext emptyList()
            try {
                Json.decodeFromString(serializer, box.open(file.readBytes()).decodeToString())
            } catch (_: GeneralSecurityException) {
                // The key is gone (app data restored elsewhere): the results can't be read back.
                emptyList()
            } catch (_: SerializationException) {
                emptyList()
            }
        }
        read.sortedBy { it.savedAtMillis }.forEach { results[it.key] = it }
        loaded = true
    }

    private suspend fun writeLocked() = withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeBytes(box.seal(Json.encodeToString(serializer, results.values.toList()).toByteArray()))
        // Atomic on the same filesystem, so a crash never leaves half a file.
        if (!temp.renameTo(file)) throw IOException("Could not save AI results")
    }
}
