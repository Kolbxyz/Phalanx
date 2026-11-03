package org.phalanx.android.core.security

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
import kotlinx.coroutines.withContext
import org.phalanx.android.di.ApplicationScope

/**
 * The duress code: typed on the lock screen in place of unlocking, it erases Phalanx from the
 * phone (see [DuressWipe]). Kept as a hash under the Keystore key, outside backups.
 */
@Singleton
class DuressCode @Inject internal constructor(
    @ApplicationContext context: Context,
    private val box: SecretBox,
    @ApplicationScope scope: CoroutineScope
) {
    private val file = File(context.noBackupFilesDir, FILE_NAME)
    private val mutableIsSet = MutableStateFlow(false)

    /** Whether a code is set; the lock screen offers code entry only then. */
    val isSet: StateFlow<Boolean> = mutableIsSet.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) { mutableIsSet.value = file.exists() }
    }

    /** [code] must pass [CodeHash.isValid]. */
    suspend fun set(code: String) = withContext(Dispatchers.IO) {
        require(CodeHash.isValid(code))
        val temp = File(file.parentFile, "$FILE_NAME.tmp")
        temp.writeBytes(box.seal(CodeHash.create(code)))
        // Atomic on the same filesystem, so a crash never leaves half a file.
        if (!temp.renameTo(file)) throw IOException("Could not save the duress code")
        mutableIsSet.value = true
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        file.delete()
        mutableIsSet.value = false
    }

    /** True when [code] is the duress code. Slow on purpose: call it off the main thread. */
    suspend fun matches(code: String): Boolean = withContext(Dispatchers.Default) {
        if (!file.exists()) return@withContext false
        val stored = try {
            box.open(file.readBytes())
        } catch (_: GeneralSecurityException) {
            // The Keystore key is gone: no code can be checked, so none matches.
            return@withContext false
        }
        CodeHash.matches(code, stored)
    }

    private companion object {
        const val FILE_NAME = "duress.bin"
    }
}
