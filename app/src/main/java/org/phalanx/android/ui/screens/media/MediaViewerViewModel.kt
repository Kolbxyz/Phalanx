package org.phalanx.android.ui.screens.media

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.data.repository.MediaRepository
import org.phalanx.android.data.repository.RoomGalleryRepository
import org.phalanx.android.data.repository.TimelineRepository
import org.phalanx.android.ui.navigation.MediaViewerRoute
import javax.inject.Inject

/**
 * Finds the photo or video in its room's (cached) timeline, or in the details screen's gallery.
 * The route only names the room and the item, never the media itself: navigation state can be
 * saved to disk, and media references carry keys.
 */
@HiltViewModel
class MediaViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    timelines: TimelineRepository,
    galleries: RoomGalleryRepository,
    private val mediaFiles: MediaRepository
) : ViewModel() {
    private val route = savedStateHandle.toRoute<MediaViewerRoute>()
    val entryId: String = route.entryId

    /** An image or a video; null until found. */
    private val mutableMedia = MutableStateFlow<MessageBody?>(null)
    val media: StateFlow<MessageBody?> = mutableMedia.asStateFlow()

    /** The decrypted video, once downloaded; played from the app's private cache. */
    private val mutableVideo = MutableStateFlow<Uri?>(null)
    val videoUri: StateFlow<Uri?> = mutableVideo.asStateFlow()

    private val mutableFailed = MutableStateFlow(false)
    val videoFailed: StateFlow<Boolean> = mutableFailed.asStateFlow()

    init {
        viewModelScope.launch {
            val entries: Flow<List<TimelineEntry>> = if (route.fromGallery) {
                galleries.current(route.roomId)?.media ?: emptyFlow()
            } else {
                timelines.open(route.roomId).getOrNull()?.entries ?: emptyFlow()
            }
            entries.map { list -> (list.firstOrNull { it.id == entryId } as? TimelineEntry.Message)?.body }
                .collect { body ->
                    val shown = body?.takeIf { it is MessageBody.Image || it is MessageBody.Video }
                    if (shown != null && mutableMedia.value == null && shown is MessageBody.Video) load(shown)
                    mutableMedia.value = shown
                }
        }
    }

    private fun load(video: MessageBody.Video) {
        viewModelScope.launch {
            mediaFiles.openable(video.media, video.filename, video.mimeType).fold(
                onSuccess = { mutableVideo.value = it },
                onFailure = { mutableFailed.value = true }
            )
        }
    }
}
