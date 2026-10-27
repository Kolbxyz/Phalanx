package org.phalanx.android.core.matrix

import java.io.IOException
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.phalanx.android.data.model.Bookmark
import org.phalanx.android.data.repository.BookmarkRepository

/** What the account data holds: ids and dates only, never a word of the messages. */
@Serializable
private data class BookmarkRef(
    @SerialName("room_id") val roomId: String,
    @SerialName("event_id") val eventId: String,
    @SerialName("saved_at") val savedAt: Long
)

@Serializable
private data class BookmarkContent(val items: List<BookmarkRef> = emptyList())

/**
 * Bookmarks in the `org.phalanx.bookmarks` account data. A change reads the server's list, writes
 * it back changed, and only then updates the local copy, so a failed write leaves both as they were.
 */
@Singleton
internal class AccountBookmarks @Inject constructor(
    private val api: ClientServerApi,
    private val store: BookmarkStore
) : BookmarkRepository {
    private val writing = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    override val bookmarks: Flow<List<Bookmark>> = store.bookmarks.onStart { store.load() }

    override suspend fun refresh(): Result<Unit> = guarded {
        val refs = fetch()
        store.update { local ->
            val byEvent = local.associateBy { it.eventId }
            refs.map { ref ->
                byEvent[ref.eventId] ?: Bookmark(ref.roomId, ref.eventId, "", "", "", ref.savedAt)
            }
        }
    }

    override suspend fun add(bookmark: Bookmark): Result<Unit> = guarded {
        val ref = BookmarkRef(bookmark.roomId, bookmark.eventId, bookmark.savedAtMillis)
        push(fetch().filterNot { it.eventId == bookmark.eventId } + ref)
        store.update { local -> local.filterNot { it.eventId == bookmark.eventId } + bookmark }
    }

    override suspend fun remove(eventId: String): Result<Unit> = guarded {
        push(fetch().filterNot { it.eventId == eventId })
        store.update { local -> local.filterNot { it.eventId == eventId } }
    }

    /** One change at a time, so two quick taps can't write over each other's list. */
    private suspend fun guarded(block: suspend () -> Unit): Result<Unit> = writing.withLock {
        try {
            block()
            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: SerializationException) {
            Result.failure(e)
        }
    }

    private suspend fun fetch(): List<BookmarkRef> {
        val response = api.call("GET", path())
        return when {
            response.code == NOT_FOUND -> emptyList()
            response.isSuccess -> json.decodeFromString<BookmarkContent>(response.body).items
            else -> throw IOException("Could not read the bookmarks (${response.code})")
        }
    }

    private suspend fun push(refs: List<BookmarkRef>) {
        val response = api.call("PUT", path(), json.encodeToString(BookmarkContent(refs)))
        if (!response.isSuccess) throw IOException("Could not save the bookmarks (${response.code})")
    }

    private fun path(): String {
        val user = URLEncoder.encode(api.session().userId, Charsets.UTF_8.name()).replace("+", "%20")
        return "user/$user/account_data/$TYPE"
    }

    private companion object {
        const val TYPE = "org.phalanx.bookmarks"
        const val NOT_FOUND = 404
    }
}
