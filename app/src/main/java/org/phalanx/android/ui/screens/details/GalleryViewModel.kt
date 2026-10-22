package org.phalanx.android.ui.screens.details

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.phalanx.android.R
import org.phalanx.android.data.model.MediaRef
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.data.repository.MediaRepository
import org.phalanx.android.data.repository.RoomGallery
import org.phalanx.android.data.repository.RoomGalleryRepository
import org.phalanx.android.ui.components.OpenRequest
import org.phalanx.android.ui.navigation.RoomGalleryRoute
import javax.inject.Inject

/** Every photo and video, or every file, of a room; older pages load only as the end comes into view. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GalleryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    galleries: RoomGalleryRepository,
    private val media: MediaRepository
) : ViewModel() {
    private val route = savedStateHandle.toRoute<RoomGalleryRoute>()
    val roomId: String = route.roomId
    val files: Boolean = route.files

    private val gallery = MutableStateFlow<RoomGallery?>(null)

    /** Null until the gallery has opened; newest first. */
    val items: StateFlow<List<TimelineEntry.Message>?> = gallery
        .flatMapLatest { it?.media ?: flowOf(null) }
        .map { all -> all?.let { sharedMediaOf(it) }?.let { if (files) it.files else it.visual } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val hasMore: StateFlow<Boolean> = gallery
        .flatMapLatest { it?.hasMoreMedia ?: flowOf(true) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /**
     * Counts the pages asked for. A page may hold nothing of this kind, so the screen keys on this,
     * not on the item count, to ask again while its end is still in view.
     */
    private val mutablePages = MutableStateFlow(0)
    val pages: StateFlow<Int> = mutablePages.asStateFlow()

    private val mutableNotice = MutableStateFlow<Int?>(null)
    val notice: StateFlow<Int?> = mutableNotice.asStateFlow()

    private val mutableOpen = MutableStateFlow<OpenRequest?>(null)
    val openRequest: StateFlow<OpenRequest?> = mutableOpen.asStateFlow()

    init {
        // The details screen under this one keeps the same gallery open, so this is instant.
        viewModelScope.launch { galleries.open(roomId).onSuccess { gallery.value = it } }
    }

    private val mutablePaging = MutableStateFlow(false)

    /** True while a page is being fetched. */
    val paging: StateFlow<Boolean> = mutablePaging.asStateFlow()

    fun loadMore() {
        val current = gallery.value ?: return
        if (mutablePaging.value) return
        mutablePaging.value = true
        viewModelScope.launch {
            // A failed page isn't counted, so the screen waits for the next scroll instead of retrying at once.
            if (current.loadMoreMedia()) mutablePages.value++
            mutablePaging.value = false
        }
    }

    fun openFile(ref: MediaRef, filename: String, mimeType: String?) {
        viewModelScope.launch {
            media.openable(ref, filename, mimeType).fold(
                onSuccess = { mutableOpen.value = OpenRequest(it, mimeType) },
                onFailure = { notify(R.string.timeline_open_failed) }
            )
        }
    }

    fun onOpened() {
        mutableOpen.value = null
    }

    fun notify(@StringRes message: Int) {
        mutableNotice.value = message
    }

    fun dismissNotice() {
        mutableNotice.value = null
    }
}
