package org.phalanx.android.core.ai

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
import kotlinx.coroutines.withContext
import org.phalanx.android.core.security.SecretBox

/**
 * The user's own Claude API key, for the AI features they chose to run in the cloud. Sealed like
 * the session (see [SecretBox]), so the file alone gives nothing away, and never logged.
 */
@Singleton
class CloudKey @Inject internal constructor(
    @ApplicationContext context: Context,
    private val box: SecretBox
) {
    private val file = File(context.filesDir, FILE_NAME)
    private val mutablePresent = MutableStateFlow(file.exists())

    /** True while a key is kept. */
    val present: StateFlow<Boolean> = mutablePresent

    /** Keeps [key] in place of the one before. */
    suspend fun set(key: String) = withContext(Dispatchers.IO) {
        file.writeBytes(box.seal(key.trim().toByteArray()))
        mutablePresent.value = true
    }

    suspend fun remove() = withContext(Dispatchers.IO) {
        file.delete()
        mutablePresent.value = false
    }

    /** The key; null when there is none, or when this phone can no longer open it. */
    internal suspend fun read(): String? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        try {
            String(box.open(file.readBytes()))
        } catch (_: GeneralSecurityException) {
            null
        } catch (_: IOException) {
            null
        }
    }

    private companion object {
        const val FILE_NAME = "ai_cloud_key"
    }
}
