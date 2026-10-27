package org.phalanx.android.core.matrix

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.phalanx.android.core.security.SecretBox
import org.phalanx.android.data.model.Bookmark

/**
 * The bookmarks, sealed with the Keystore key like the reminders: they quote messages, which never
 * sit on disk in the clear. Read once, then kept in memory.
 */
@Singleton
internal class BookmarkStore @Inject constructor(
    @ApplicationContext context: Context,
    private val box: SecretBox
) {
    private val file = File(context.filesDir, FILE_NAME)
    private val lock = Mutex()
    private var loaded = false
    private val mutableBookmarks = MutableStateFlow<List<Bookmark>>(emptyList())

    /** Newest first; empty until [load] has read the file. */
    val bookmarks: StateFlow<List<Bookmark>> = mutableBookmarks

    suspend fun load(): List<Bookmark> = lock.withLock { loadLocked() }

    suspend fun update(transform: (List<Bookmark>) -> List<Bookmark>) = lock.withLock {
        val next = transform(loadLocked()).sortedByDescending { it.savedAtMillis }
        withContext(Dispatchers.IO) {
            val temp = File(file.parentFile, "$FILE_NAME.tmp")
            temp.writeBytes(box.seal(Json.encodeToString(next).toByteArray()))
            // Atomic on the same filesystem, so a crash never leaves half a file.
            if (!temp.renameTo(file)) throw IOException("Could not save bookmarks")
        }
        mutableBookmarks.value = next
    }

    private suspend fun loadLocked(): List<Bookmark> {
        if (loaded) return mutableBookmarks.value
        val read = withContext(Dispatchers.IO) {
            if (!file.exists()) return@withContext emptyList()
            try {
                Json.decodeFromString<List<Bookmark>>(box.open(file.readBytes()).decodeToString())
            } catch (_: GeneralSecurityException) {
                // The key is gone (app data restored elsewhere): the previews can't be read back,
                // and a refresh brings the bookmarks themselves back from the account.
                emptyList()
            } catch (_: SerializationException) {
                emptyList()
            }
        }
        mutableBookmarks.value = read
        loaded = true
        return read
    }

    private companion object {
        const val FILE_NAME = "bookmarks.bin"
    }
}
