package org.phalanx.android.core.contacts

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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.phalanx.android.core.matrix.ActiveClient
import org.phalanx.android.core.security.SecretBox
import org.phalanx.android.data.model.PeopleLinks
import org.phalanx.android.data.model.SessionState
import org.phalanx.android.data.model.pairKey
import org.phalanx.android.data.repository.PeopleRepository
import org.phalanx.android.di.ApplicationScope

/**
 * The people the user linked, sealed with the Keystore key: who someone talks to on which network
 * says a lot about them, so it never sits on disk in the clear. Deleted on sign-out.
 */
@Singleton
internal class PeopleStore @Inject constructor(
    @ApplicationContext context: Context,
    private val box: SecretBox,
    active: ActiveClient,
    @ApplicationScope scope: CoroutineScope
) : PeopleRepository {
    private val file = File(context.filesDir, FILE_NAME)
    private val lock = Mutex()
    private val mutableLinks = MutableStateFlow<PeopleLinks?>(null)
    override val links: StateFlow<PeopleLinks?> = mutableLinks.asStateFlow()

    init {
        scope.launch { lock.withLock { mutableLinks.value = read() } }
        // Signed out: the links were the account's, and go with it.
        scope.launch {
            active.state.filter { it is SessionState.LoggedOut }.collect {
                lock.withLock {
                    withContext(Dispatchers.IO) { file.delete() }
                    mutableLinks.value = PeopleLinks()
                }
            }
        }
    }

    override suspend fun link(a: String, b: String) = update { links ->
        val joined = links.groups.filter { a in it || b in it }
        links.copy(groups = links.groups - joined.toSet() + setOf(joined.flatten().toSet() + a + b))
    }

    override suspend fun unlink(keys: Set<String>) = update { links ->
        links.copy(groups = links.groups.map { it - keys }.filter { it.size > 1 })
    }

    override suspend fun dismiss(a: String, b: String) = update { it.copy(dismissed = it.dismissed + pairKey(a, b)) }

    private suspend fun update(transform: (PeopleLinks) -> PeopleLinks) = lock.withLock {
        val next = transform(mutableLinks.value ?: read())
        withContext(Dispatchers.IO) {
            val temp = File(file.parentFile, "$FILE_NAME.tmp")
            temp.writeBytes(box.seal(Json.encodeToString(next).toByteArray()))
            // Atomic on the same filesystem, so a crash never leaves half a file.
            if (!temp.renameTo(file)) throw IOException("Could not save people")
        }
        mutableLinks.value = next
    }

    private suspend fun read(): PeopleLinks = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext PeopleLinks()
        try {
            Json.decodeFromString<PeopleLinks>(box.open(file.readBytes()).decodeToString())
        } catch (_: GeneralSecurityException) {
            // The key is gone (app data restored elsewhere): the links can't be read back.
            PeopleLinks()
        } catch (_: SerializationException) {
            PeopleLinks()
        }
    }

    private companion object {
        const val FILE_NAME = "people.bin"
    }
}
