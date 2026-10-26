package org.phalanx.android.core.matrix

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.phalanx.android.core.security.SecretBox
import org.phalanx.android.data.model.ArchivedKind
import org.phalanx.android.data.model.DeletedMessage
import org.phalanx.android.data.repository.DeletedMessagesRepository
import org.phalanx.android.di.ApplicationScope

@Serializable
internal data class ArchivedMessage(
    val eventId: String,
    val roomId: String,
    val roomName: String,
    val senderId: String,
    val kind: ArchivedKind,
    val text: String,
    val sentAtMillis: Long,
    val edited: Boolean = false,
    /** Looked up when the deletion arrives, so a copy that is never deleted costs no member lookup. */
    val senderName: String? = null,
    val deletedAtMillis: Long? = null
)

/**
 * Copies of received messages, sealed with the Keystore key like the session: they are message
 * text, which never sits on disk in the clear. Copies that were not deleted expire after [RETENTION_DAYS];
 * deleted ones stay until the user clears them or turns the setting off.
 */
@Singleton
internal class MessageArchive @Inject constructor(
    @ApplicationContext context: Context,
    private val box: SecretBox,
    @ApplicationScope scope: CoroutineScope
) : DeletedMessagesRepository {
    private val file = File(context.filesDir, FILE_NAME)
    private val lock = Mutex()
    private var loaded = false

    // Keyed by event ID, guarded by [lock].
    private val messages = HashMap<String, ArchivedMessage>()
    private val mutableDeleted = MutableStateFlow<List<DeletedMessage>>(emptyList())
    override val deleted: StateFlow<List<DeletedMessage>> = mutableDeleted

    // Messages arrive in bursts while catching up; one write per burst instead of one per message.
    private val pendingSave = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            for (request in pendingSave) {
                delay(SAVE_DELAY_MILLIS)
                try {
                    lock.withLock { writeLocked() }
                } catch (_: IOException) {
                    // A full disk, say: the copies are still in memory and the next message retries.
                }
            }
        }
    }

    /** Reads the file once, so the deleted list shows before any new event arrives. */
    suspend fun load() = lock.withLock { loadLocked() }

    /** Applies [change] to the copies keyed by event ID; the write follows shortly after. */
    suspend fun update(change: (MutableMap<String, ArchivedMessage>) -> Unit) = lock.withLock {
        loadLocked()
        change(messages)
        prune(System.currentTimeMillis())
        publish()
        pendingSave.trySend(Unit)
    }

    /** Who sent the kept copy of [eventId]; null when there is none to mark deleted. */
    suspend fun senderOf(eventId: String): String? = lock.withLock {
        loadLocked()
        messages[eventId]?.senderId
    }

    override suspend fun clear() = lock.withLock {
        messages.clear()
        loaded = true
        publish()
        withContext(Dispatchers.IO) { file.delete() }
        Unit
    }

    private fun prune(now: Long) {
        val cutoff = now - TimeUnit.DAYS.toMillis(RETENTION_DAYS)
        messages.values.removeAll { it.deletedAtMillis == null && it.sentAtMillis < cutoff }
        val live = messages.values.filter { it.deletedAtMillis == null }
        // A very busy week is capped by dropping the oldest copies first.
        if (live.size > MAX_COPIES) {
            live.sortedBy { it.sentAtMillis }.take(live.size - MAX_COPIES).forEach { messages.remove(it.eventId) }
        }
    }

    private fun publish() {
        mutableDeleted.value = messages.values
            .mapNotNull { it.toDeleted() }
            .sortedByDescending { it.deletedAtMillis }
    }

    private suspend fun loadLocked() {
        if (loaded) return
        val read = withContext(Dispatchers.IO) {
            if (!file.exists()) return@withContext emptyList()
            try {
                Json.decodeFromString<List<ArchivedMessage>>(box.open(file.readBytes()).decodeToString())
            } catch (_: GeneralSecurityException) {
                // The key is gone (app data restored elsewhere): the copies can't be read back.
                emptyList()
            } catch (_: SerializationException) {
                emptyList()
            }
        }
        read.forEach { messages[it.eventId] = it }
        loaded = true
        publish()
    }

    private suspend fun writeLocked() = withContext(Dispatchers.IO) {
        // Cleared or turned off since the write was asked for: nothing may be left behind.
        if (messages.isEmpty()) {
            file.delete()
            return@withContext
        }
        val sealed = box.seal(Json.encodeToString(messages.values.toList()).toByteArray())
        val temp = File(file.parentFile, "$FILE_NAME.tmp")
        temp.writeBytes(sealed)
        // Atomic on the same filesystem, so a crash never leaves half a file.
        if (!temp.renameTo(file)) throw IOException("Could not save kept messages")
    }

    private companion object {
        const val FILE_NAME = "kept_messages.bin"
        const val RETENTION_DAYS = 7L
        const val MAX_COPIES = 20_000
        const val SAVE_DELAY_MILLIS = 3_000L
    }
}

private fun ArchivedMessage.toDeleted(): DeletedMessage? = deletedAtMillis?.let {
    DeletedMessage(eventId, roomName, senderName ?: senderId, kind, text, edited, sentAtMillis, it)
}
