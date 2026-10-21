package org.phalanx.android.core.schedule

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.phalanx.android.core.security.SecretBox
import org.phalanx.android.data.model.ScheduledMessage
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The scheduled messages, sealed with the Keystore key like the session: they are message text,
 * which never sits on disk in the clear. Read once, then kept in memory.
 */
@Singleton
internal class ScheduledStore @Inject constructor(
    @ApplicationContext context: Context,
    private val box: SecretBox
) {
    private val file = File(context.filesDir, FILE_NAME)
    private val lock = Mutex()
    private var loaded = false
    private val mutableMessages = MutableStateFlow<List<ScheduledMessage>>(emptyList())

    /** Empty until [load] has read the file. */
    val messages: StateFlow<List<ScheduledMessage>> = mutableMessages

    suspend fun load(): List<ScheduledMessage> = lock.withLock { loadLocked() }

    suspend fun update(transform: (List<ScheduledMessage>) -> List<ScheduledMessage>) = lock.withLock {
        val next = transform(loadLocked()).sortedBy { it.sendAtMillis }
        withContext(Dispatchers.IO) {
            val sealed = box.seal(Json.encodeToString(next).toByteArray())
            val temp = File(file.parentFile, "$FILE_NAME.tmp")
            temp.writeBytes(sealed)
            // Atomic on the same filesystem, so a crash never leaves half a file.
            if (!temp.renameTo(file)) throw IOException("Could not save scheduled messages")
        }
        mutableMessages.value = next
    }

    private suspend fun loadLocked(): List<ScheduledMessage> {
        if (loaded) return mutableMessages.value
        val read = withContext(Dispatchers.IO) {
            if (!file.exists()) return@withContext emptyList()
            try {
                Json.decodeFromString<List<ScheduledMessage>>(box.open(file.readBytes()).decodeToString())
            } catch (_: GeneralSecurityException) {
                // The key is gone (app data restored elsewhere): the messages can't be read back.
                emptyList()
            } catch (_: SerializationException) {
                emptyList()
            }
        }
        mutableMessages.value = read
        loaded = true
        return read
    }

    private companion object {
        const val FILE_NAME = "scheduled.bin"
    }
}
