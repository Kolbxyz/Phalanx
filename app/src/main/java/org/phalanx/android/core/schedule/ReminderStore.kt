package org.phalanx.android.core.schedule

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
import org.phalanx.android.data.model.Reminder

/**
 * The reminders, sealed with the Keystore key like the session: they quote messages, which never
 * sit on disk in the clear. Read once, then kept in memory.
 */
@Singleton
internal class ReminderStore @Inject constructor(
    @ApplicationContext context: Context,
    private val box: SecretBox
) {
    private val file = File(context.filesDir, FILE_NAME)
    private val lock = Mutex()
    private var loaded = false
    private val mutableReminders = MutableStateFlow<List<Reminder>>(emptyList())

    /** Empty until [load] has read the file. */
    val reminders: StateFlow<List<Reminder>> = mutableReminders

    suspend fun load(): List<Reminder> = lock.withLock { loadLocked() }

    suspend fun update(transform: (List<Reminder>) -> List<Reminder>) = lock.withLock {
        val next = transform(loadLocked()).sortedBy { it.remindAtMillis }
        withContext(Dispatchers.IO) {
            val temp = File(file.parentFile, "$FILE_NAME.tmp")
            temp.writeBytes(box.seal(Json.encodeToString(next).toByteArray()))
            // Atomic on the same filesystem, so a crash never leaves half a file.
            if (!temp.renameTo(file)) throw IOException("Could not save reminders")
        }
        mutableReminders.value = next
    }

    private suspend fun loadLocked(): List<Reminder> {
        if (loaded) return mutableReminders.value
        val read = withContext(Dispatchers.IO) {
            if (!file.exists()) return@withContext emptyList()
            try {
                Json.decodeFromString<List<Reminder>>(box.open(file.readBytes()).decodeToString())
            } catch (_: GeneralSecurityException) {
                // The key is gone (app data restored elsewhere): the reminders can't be read back.
                emptyList()
            } catch (_: SerializationException) {
                emptyList()
            }
        }
        mutableReminders.value = read
        loaded = true
        return read
    }

    private companion object {
        const val FILE_NAME = "reminders.bin"
    }
}
