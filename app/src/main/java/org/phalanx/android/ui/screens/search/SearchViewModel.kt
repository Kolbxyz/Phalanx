package org.phalanx.android.ui.screens.search

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.phalanx.android.R
import org.phalanx.android.core.ai.AiDisk
import org.phalanx.android.core.ai.photos.PhotoNote
import org.phalanx.android.core.ai.photos.matches
import org.phalanx.android.data.model.MediaRef
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.model.RoomSummary
import org.phalanx.android.data.repository.MediaRepository
import org.phalanx.android.data.repository.MessageHit
import org.phalanx.android.data.repository.MessageSearch
import org.phalanx.android.data.repository.RoomListRepository
import org.phalanx.android.data.repository.SearchRepository
import org.phalanx.android.ui.components.Notice
import org.phalanx.android.ui.components.NoticeKind
import org.phalanx.android.ui.components.OpenRequest

enum class SearchCategory(@StringRes val label: Int) {
    All(R.string.search_all),
    Chats(R.string.search_chats),
    Messages(R.string.search_messages),
    Media(R.string.search_media),
    Files(R.string.search_files)
}

/** What matches the query, split by kind; [roomNames] labels each message with its conversation. */
@Immutable
data class SearchResults(
    val chats: List<RoomSummary> = emptyList(),
    val messages: List<MessageHit> = emptyList(),
    val media: List<MessageHit> = emptyList(),
    val files: List<MessageHit> = emptyList(),
    val roomNames: Map<String, String> = emptyMap(),
    val loading: Boolean = false,
    val endReached: Boolean = true
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    roomList: RoomListRepository,
    private val searches: SearchRepository,
    private val mediaFiles: MediaRepository,
    disk: AiDisk
) : ViewModel() {
    private val mutableQuery = MutableStateFlow("")
    val query: StateFlow<String> = mutableQuery.asStateFlow()

    private val mutableCategory = MutableStateFlow(SearchCategory.All)
    val category: StateFlow<SearchCategory> = mutableCategory.asStateFlow()

    private val search = MutableStateFlow<MessageSearch?>(null)

    private val mutableNotice = MutableStateFlow<Notice?>(null)
    val notice: StateFlow<Notice?> = mutableNotice.asStateFlow()

    private val mutableOpen = MutableStateFlow<OpenRequest?>(null)
    val openRequest: StateFlow<OpenRequest?> = mutableOpen.asStateFlow()

    private val hits = search.flatMapLatest { it?.hits ?: flowOf(emptyList()) }
    private val paging = search.flatMapLatest { current ->
        current?.let { combine(it.loading, it.endReached) { loading, end -> loading to end } } ?: flowOf(false to true)
    }

    // Photos found by what they show, from the descriptions kept on this phone (see PhotoIndexer).
    private val photoHits = mutableQuery.debounce(DEBOUNCE_MILLIS).distinctUntilChanged().mapLatest { text ->
        val query = text.trim()
        if (query.isEmpty()) {
            emptyList()
        } else {
            disk.readAll(disk.photos).filterValues { it.matches(query) }.map { (eventId, note) -> note.toHit(eventId) }
                .sortedByDescending { it.timestampMillis }
        }
    }

    val results: StateFlow<SearchResults> = combine(
        roomList.rooms,
        mutableQuery,
        combine(hits, photoHits) { found, photos -> found to photos },
        paging
    ) { rooms, query, (allHits, allPhotos), page ->
        val all = rooms.orEmpty()
        val text = query.trim()
        // A cleared query keeps the last hits until the index answers; none of them should show.
        val found = if (text.isEmpty()) emptyList() else allHits
        val photos = if (text.isEmpty()) emptyList() else allPhotos
        SearchResults(
            chats = if (text.isEmpty()) emptyList() else all.filter { it.name.contains(text, ignoreCase = true) },
            messages = found.filter { it.body is MessageBody.Text },
            media = (found.filter { it.body is MessageBody.Image || it.body is MessageBody.Video } + photos)
                .distinctBy { it.eventId },
            files = found.filter { it.body is MessageBody.File || it.body is MessageBody.Audio },
            roomNames = all.associate { it.id to it.name },
            loading = page.first,
            endReached = page.second
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SearchResults())

    init {
        viewModelScope.launch {
            search.value = searches.open()
            // Waits for a pause in typing, so the index is not queried on every key.
            mutableQuery.debounce(DEBOUNCE_MILLIS).distinctUntilChanged().collect { text ->
                search.value?.setQuery(text)?.onFailure {
                    mutableNotice.value = Notice(R.string.search_failed, NoticeKind.Error)
                }
            }
        }
    }

    fun onQueryChange(text: String) {
        mutableQuery.value = text
    }

    fun onCategory(value: SearchCategory) {
        mutableCategory.value = value
    }

    fun loadMore() {
        val current = search.value ?: return
        viewModelScope.launch { current.loadMore() }
    }

    fun openFile(hit: MessageHit) {
        val (media, name, mime) = when (val body = hit.body) {
            is MessageBody.File -> Triple(body.media, body.filename, body.mimeType)
            is MessageBody.Audio -> Triple(body.media, body.filename, body.mimeType)
            else -> return
        }
        viewModelScope.launch {
            mediaFiles.openable(media, name, mime).fold(
                onSuccess = { mutableOpen.value = OpenRequest(it, mime) },
                onFailure = { mutableNotice.value = Notice(R.string.timeline_open_failed, NoticeKind.Error) }
            )
        }
    }

    fun onNoApp() {
        mutableNotice.value = Notice(R.string.timeline_open_failed, NoticeKind.Error)
    }

    fun onOpened() {
        mutableOpen.value = null
    }

    fun dismissNotice() {
        mutableNotice.value = null
    }

    /** Called when the overlay closes, so it opens empty next time. */
    fun reset() {
        mutableQuery.value = ""
        mutableCategory.value = SearchCategory.All
    }

    override fun onCleared() {
        search.value?.close()
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 300L
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** A described photo as a search hit, shown by its thumbnail like any media hit. */
private fun PhotoNote.toHit(eventId: String) = MessageHit(
    roomId = roomId,
    eventId = eventId,
    senderName = "",
    senderAvatar = null,
    body = MessageBody.Image(
        media = MediaRef(thumbnail),
        thumbnail = null,
        width = null,
        height = null,
        caption = null,
        isSticker = false,
        filename = "",
        mimeType = mimeType
    ),
    timestampMillis = sentAtMillis
)
