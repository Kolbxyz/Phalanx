package org.phalanx.android.core.media

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
import org.phalanx.android.data.model.KitchenMix
import org.phalanx.android.data.model.KitchenPicks

/**
 * The user's recent and starred Emoji Kitchen mixes, on this phone only and sealed with the
 * Keystore key like the scheduled messages: what someone sends says something about them.
 */
@Singleton
internal class KitchenStore @Inject constructor(
    @ApplicationContext context: Context,
    private val box: SecretBox
) {
    private val file = File(context.filesDir, FILE_NAME)
    private val lock = Mutex()
    private var loaded = false
    private val mutablePicks = MutableStateFlow(KitchenPicks())

    /** Empty until [load] has read the file. */
    val picks: StateFlow<KitchenPicks> = mutablePicks

    suspend fun load(): KitchenPicks = lock.withLock { loadLocked() }

    /** Puts [mix] first among the recent ones. */
    suspend fun sent(mix: KitchenMix): Result<Unit> = update { picks ->
        picks.copy(recent = (listOf(mix) + picks.recent.filterNot { it == mix }).take(MAX_RECENT))
    }

    suspend fun toggleFavourite(mix: KitchenMix): Result<Unit> = update { picks ->
        val favourites = if (mix in picks.favourites) picks.favourites - mix else listOf(mix) + picks.favourites
        picks.copy(favourites = favourites.take(MAX_FAVOURITES))
    }

    private suspend fun update(transform: (KitchenPicks) -> KitchenPicks): Result<Unit> = lock.withLock {
        val next = transform(loadLocked())
        withContext(Dispatchers.IO) {
            try {
                val temp = File(file.parentFile, "$FILE_NAME.tmp")
                temp.writeBytes(box.seal(Json.encodeToString(next).toByteArray()))
                // Atomic on the same filesystem, so a crash never leaves half a file.
                if (!temp.renameTo(file)) throw IOException("Could not save Emoji Kitchen picks")
                Result.success(Unit)
            } catch (e: IOException) {
                Result.failure(e)
            }
        }.onSuccess { mutablePicks.value = next }
    }

    private suspend fun loadLocked(): KitchenPicks {
        if (loaded) return mutablePicks.value
        val read = withContext(Dispatchers.IO) {
            if (!file.exists()) return@withContext KitchenPicks()
            try {
                Json.decodeFromString<KitchenPicks>(box.open(file.readBytes()).decodeToString())
            } catch (_: GeneralSecurityException) {
                // The key is gone (app data restored elsewhere): the picks can't be read back.
                KitchenPicks()
            } catch (_: SerializationException) {
                KitchenPicks()
            }
        }
        mutablePicks.value = read
        loaded = true
        return read
    }

    private companion object {
        const val FILE_NAME = "kitchen_picks.bin"
        const val MAX_RECENT = 16
        const val MAX_FAVOURITES = 32
    }
}
