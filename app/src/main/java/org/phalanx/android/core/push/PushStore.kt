package org.phalanx.android.core.push

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.phalanx.android.core.security.SecretBox

/** The pusher this device last gave the homeserver, so a changed endpoint can replace it. */
@Serializable
internal data class PushRegistration(val pushKey: String, val gatewayUrl: String)

/**
 * Sealed by [SecretBox]: the endpoint is a capability URL, and anyone holding it can wake this phone.
 */
@Singleton
internal class PushStore @Inject constructor(
    @ApplicationContext context: Context,
    private val secretBox: SecretBox
) {
    private val file = File(context.filesDir, FILE_NAME)

    @Synchronized
    fun load(): PushRegistration? {
        if (!file.exists()) return null
        return try {
            Json.decodeFromString(PushRegistration.serializer(), String(secretBox.open(file.readBytes())))
        } catch (_: GeneralSecurityException) {
            // Unreadable means the Keystore key is gone; the registration is simply made again.
            file.delete()
            null
        } catch (_: SerializationException) {
            file.delete()
            null
        }
    }

    @Synchronized
    fun save(registration: PushRegistration) {
        val temp = File(file.parentFile, "$FILE_NAME.tmp")
        temp.writeBytes(secretBox.seal(Json.encodeToString(PushRegistration.serializer(), registration).toByteArray()))
        if (!temp.renameTo(file)) throw IOException("Could not replace the push registration file")
    }

    @Synchronized
    fun clear() {
        file.delete()
    }

    private companion object {
        const val FILE_NAME = "push.bin"
    }
}
