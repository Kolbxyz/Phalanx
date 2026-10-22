package org.phalanx.android.ui.screens.media

import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.phalanx.android.R
import org.phalanx.android.data.model.MediaRef
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.data.repository.MediaRepository
import org.phalanx.android.data.repository.RoomGallery
import org.phalanx.android.data.repository.RoomGalleryRepository
import org.phalanx.android.data.repository.TimelineRepository
import org.phalanx.android.data.repository.TimelineSession
import org.phalanx.android.ui.components.Notice
import org.phalanx.android.ui.components.NoticeKind
import org.phalanx.android.ui.components.OpenRequest
import org.phalanx.android.ui.navigation.MediaViewerRoute
import javax.inject.Inject

/** A video page's file: downloading, ready to play, or failed. */
@Immutable
sealed interface VideoFile {
    data object Loading : VideoFile
    data class Ready(val uri: Uri) : VideoFile
    data object Failed : VideoFile
}

/**
 * Every photo and video of the room's (cached) timeline, or of the details screen's gallery, oldest
 * first, so swiping moves through them in order. The route only names the room and the item the
 * viewer opened on, never the media itself: navigation state can be saved to disk, and media
 * references carry keys.
 */
@HiltViewModel
class MediaViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val timelines: TimelineRepository,
    private val galleries: RoomGalleryRepository,
    private val mediaFiles: MediaRepository
) : ViewModel() {
    private val route = savedStateHandle.toRoute<MediaViewerRoute>()
    val entryId: String = route.entryId
    val roomId: String = route.roomId

    /** Null until the source is open. */
    private val mutableItems = MutableStateFlow<List<TimelineEntry.Message>?>(null)
    val items: StateFlow<List<TimelineEntry.Message>?> = mutableItems.asStateFlow()

    private val mutableVideos = MutableStateFlow<Map<String, VideoFile>>(emptyMap())
    val videos: StateFlow<Map<String, VideoFile>> = mutableVideos.asStateFlow()

    private val mutableNotice = MutableStateFlow<Notice?>(null)
    val notice: StateFlow<Notice?> = mutableNotice.asStateFlow()

    private val mutableShare = MutableStateFlow<OpenRequest?>(null)
    val shareRequest: StateFlow<OpenRequest?> = mutableShare.asStateFlow()

    /** Below Android 10 saving goes through a document picker, which the screen launches. */
    val savesThroughPicker: Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    private var session: TimelineSession? = null
    private var gallery: RoomGallery? = null

    init {
        viewModelScope.launch {
            val source = if (route.fromGallery) {
                galleries.current(route.roomId)?.also { gallery = it }?.media?.map { it.asReversed() }
            } else {
                timelines.open(route.roomId).getOrNull()?.also { session = it }?.entries
            }
            if (source == null) {
                mutableItems.value = emptyList()
                return@launch
            }
            source.map { list -> list.filterIsInstance<TimelineEntry.Message>().filter { it.body.isViewable() } }
                .collect { mutableItems.value = it }
        }
    }

    /** Called when the first (oldest) item is shown, to page in what came before it. */
    fun loadOlder() {
        viewModelScope.launch {
            gallery?.loadMoreMedia() ?: session?.loadOlder()
        }
    }

    fun loadVideo(entry: TimelineEntry.Message) {
        val video = entry.body as? MessageBody.Video ?: return
        if (mutableVideos.value[entry.id].let { it != null && it != VideoFile.Failed }) return
        mutableVideos.update { it + (entry.id to VideoFile.Loading) }
        viewModelScope.launch {
            val file = mediaFiles.openable(video.media, video.filename, video.mimeType).fold(
                onSuccess = { VideoFile.Ready(it) },
                onFailure = { VideoFile.Failed }
            )
            mutableVideos.update { it + (entry.id to file) }
        }
    }

    fun share(entry: TimelineEntry.Message) {
        val file = entry.body.fileOf() ?: return
        viewModelScope.launch {
            mediaFiles.openable(file.media, file.filename, file.mimeType).fold(
                onSuccess = { mutableShare.value = OpenRequest(it, file.mimeType) },
                onFailure = { mutableNotice.value = Notice(R.string.timeline_open_failed, NoticeKind.Error) }
            )
        }
    }

    fun onShared() {
        mutableShare.value = null
    }

    fun saveToGallery(entry: TimelineEntry.Message) {
        val file = entry.body.fileOf() ?: return
        viewModelScope.launch {
            report(mediaFiles.saveToGallery(file.media, file.filename, file.mimeType))
        }
    }

    fun saveTo(entry: TimelineEntry.Message, destination: Uri) {
        val file = entry.body.fileOf() ?: return
        viewModelScope.launch { report(mediaFiles.saveTo(file.media, destination)) }
    }

    fun dismissNotice() {
        mutableNotice.value = null
    }

    private fun report(result: Result<Unit>) {
        mutableNotice.value = result.fold(
            onSuccess = { Notice(R.string.media_saved, NoticeKind.Done) },
            onFailure = { Notice(R.string.media_save_failed, NoticeKind.Error) }
        )
    }
}

private fun MessageBody.isViewable() = this is MessageBody.Image || this is MessageBody.Video

/** What a viewer page saves or shares. */
internal class MediaFile(
    val media: MediaRef,
    val filename: String,
    val mimeType: String?
)

internal fun MessageBody.fileOf(): MediaFile? = when (this) {
    is MessageBody.Image -> MediaFile(media, filename, mimeType)
    is MessageBody.Video -> MediaFile(media, filename, mimeType)
    else -> null
}
