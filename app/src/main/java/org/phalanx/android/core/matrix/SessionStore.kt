package org.phalanx.android.core.matrix

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.phalanx.android.core.security.SecretBox

/**
 * The one signed-in account, kept in a single file sealed by [SecretBox]. Calls are synchronized
 * because the SDK reports refreshed tokens from its own threads.
 */
@Singleton
internal class SessionStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secretBox: SecretBox
) {
    private val file = File(context.filesDir, FILE_NAME)
    private var loaded = false
    private var cached: SessionData? = null

    @Synchronized
    fun load(): SessionData? {
        if (!loaded) {
            cached = read()
            loaded = true
        }
        return cached
    }

    @Synchronized
    fun save(data: SessionData) {
        // Write beside the target and rename, so a crash mid-write cannot leave a truncated session.
        val temp = File(file.parentFile, "$FILE_NAME.tmp")
        temp.writeBytes(secretBox.seal(Json.encodeToString(SessionData.serializer(), data).toByteArray()))
        if (!temp.renameTo(file)) throw IOException("Could not replace the session file")
        cached = data
        loaded = true
    }

    /** Applies [transform] to the stored session; does nothing when signed out. */
    @Synchronized
    fun update(transform: (SessionData) -> SessionData) {
        load()?.let { save(transform(it)) }
    }

    @Synchronized
    fun clear() {
        file.delete()
        cached = null
        loaded = true
    }

    /**
     * An unreadable file means the Keystore key is gone (a restore onto a new device, or a reset
     * lock screen) or the file is corrupt. Either way the SDK store it protected can no longer be
     * opened, so the only honest answer is "signed out" and the file is removed.
     */
    private fun read(): SessionData? {
        if (!file.exists()) return null
        return try {
            Json.decodeFromString(SessionData.serializer(), String(secretBox.open(file.readBytes())))
        } catch (_: GeneralSecurityException) {
            file.delete()
            null
        } catch (_: SerializationException) {
            file.delete()
            null
        }
    }

    private companion object {
        const val FILE_NAME = "session.bin"
    }
}
