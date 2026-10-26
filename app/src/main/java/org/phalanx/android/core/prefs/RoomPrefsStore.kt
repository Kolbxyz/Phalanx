package org.phalanx.android.core.prefs

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.phalanx.android.core.security.SecretBox
import org.phalanx.android.data.model.RoomPrefs
import org.phalanx.android.di.ApplicationScope

/**
 * Choices made on this phone for single rooms, by room ID. Sealed with the Keystore key: which
 * rooms someone protects or watches says something about them, so it never sits on disk in the
 * clear. Rooms left at the defaults are not stored.
 */
@Singleton
class RoomPrefsStore @Inject internal constructor(
    @ApplicationContext context: Context,
    private val box: SecretBox,
    @ApplicationScope scope: CoroutineScope
) {
    private val file = File(context.filesDir, FILE_NAME)
    private val lock = Mutex()
    private val mutablePrefs = MutableStateFlow<Map<String, RoomPrefs>?>(null)

    /** Null until the file has been read, which starts with the app. */
    val prefs: StateFlow<Map<String, RoomPrefs>?> = mutablePrefs.asStateFlow()

    init {
        scope.launch { lock.withLock { mutablePrefs.value = read() } }
    }

    suspend fun update(roomId: String, transform: (RoomPrefs) -> RoomPrefs) = lock.withLock {
        val all = mutablePrefs.value ?: read()
        val next = transform(all[roomId] ?: RoomPrefs())
        val updated = if (next == RoomPrefs()) all - roomId else all + (roomId to next)
        withContext(Dispatchers.IO) {
            val temp = File(file.parentFile, "$FILE_NAME.tmp")
            temp.writeBytes(box.seal(Json.encodeToString(updated).toByteArray()))
            // Atomic on the same filesystem, so a crash never leaves half a file.
            if (!temp.renameTo(file)) throw IOException("Could not save room preferences")
        }
        mutablePrefs.value = updated
    }

    private suspend fun read(): Map<String, RoomPrefs> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyMap()
        try {
            Json.decodeFromString<Map<String, RoomPrefs>>(box.open(file.readBytes()).decodeToString())
        } catch (_: GeneralSecurityException) {
            // The key is gone (app data restored elsewhere): the choices can't be read back.
            emptyMap()
        } catch (_: SerializationException) {
            emptyMap()
        }
    }

    private companion object {
        const val FILE_NAME = "room_prefs.bin"
    }
}
