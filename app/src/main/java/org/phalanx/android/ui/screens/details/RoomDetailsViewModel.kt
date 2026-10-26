package org.phalanx.android.ui.screens.details

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.phalanx.android.R
import org.phalanx.android.core.prefs.RoomPrefsStore
import org.phalanx.android.data.model.MediaRef
import org.phalanx.android.data.model.Participant
import org.phalanx.android.data.model.RoomPrefs
import org.phalanx.android.data.model.RoomSummary
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.data.model.UserTrust
import org.phalanx.android.data.repository.BlockedUsersRepository
import org.phalanx.android.data.repository.MediaRepository
import org.phalanx.android.data.repository.RoomActionsRepository
import org.phalanx.android.data.repository.RoomGallery
import org.phalanx.android.data.repository.RoomGalleryRepository
import org.phalanx.android.data.repository.RoomListRepository
import org.phalanx.android.data.repository.SecurityRepository
import org.phalanx.android.ui.components.OpenRequest
import org.phalanx.android.ui.navigation.RoomDetailsRoute

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RoomDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    roomList: RoomListRepository,
    galleries: RoomGalleryRepository,
    private val actions: RoomActionsRepository,
    private val blockedUsers: BlockedUsersRepository,
    private val media: MediaRepository,
    private val security: SecurityRepository,
    private val roomPrefs: RoomPrefsStore
) : ViewModel() {
    val roomId: String = savedStateHandle.toRoute<RoomDetailsRoute>().roomId

    /** Live from the room list, so a favourite or a rename shows at once. */
    val room: StateFlow<RoomSummary?> = roomList.rooms
        .map { rooms -> rooms?.firstOrNull { it.id == roomId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, roomList.rooms.value?.firstOrNull { it.id == roomId })

    /** What was chosen on this phone for the room: screen protection and alerts. */
    val prefs: StateFlow<RoomPrefs> = roomPrefs.prefs
        .map { it?.get(roomId) ?: RoomPrefs() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, roomPrefs.prefs.value?.get(roomId) ?: RoomPrefs())

    /** For a one-to-one chat, whether the other person is verified; null for a group or until known. */
    val trust: StateFlow<UserTrust?> = room
        .map { it?.dmUserId }
        .distinctUntilChanged()
        .mapLatest { userId -> userId?.let { security.trustOf(it) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val gallery = MutableStateFlow<RoomGallery?>(null)

    val pinned: StateFlow<List<TimelineEntry.Message>?> = gallery
        .flatMapLatest { it?.pinned ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val mediaItems: StateFlow<List<TimelineEntry.Message>?> = gallery
        .flatMapLatest { it?.media ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val hasMoreMedia: StateFlow<Boolean> = gallery
        .flatMapLatest { it?.hasMoreMedia ?: flowOf(true) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** Null until the gallery has opened and loaded them. */
    val members: StateFlow<List<Participant>?> = gallery
        .mapLatest { it?.members() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val mutableNotice = MutableStateFlow<Int?>(null)
    val notice: StateFlow<Int?> = mutableNotice.asStateFlow()

    private val mutableOpen = MutableStateFlow<OpenRequest?>(null)
    val openRequest: StateFlow<OpenRequest?> = mutableOpen.asStateFlow()

    /** Set once the room has been left, so the screen can go back to the list. */
    private val mutableLeft = MutableStateFlow(false)
    val left: StateFlow<Boolean> = mutableLeft.asStateFlow()

    init {
        viewModelScope.launch {
            galleries.open(roomId).onSuccess { opened ->
                gallery.value = opened
                // A busy room's first page may hold no media at all; look a little further back,
                // never through the whole history: that is the gallery screen's, page by page.
                repeat(EXTRA_PAGES) {
                    if (opened.media.value.isNotEmpty() || !opened.hasMoreMedia.value) return@onSuccess
                    opened.loadMoreMedia()
                }
            }.onFailure { mutableNotice.value = R.string.details_gallery_failed }
        }
    }

    fun setFavourite(favourite: Boolean) = act { actions.setFavourite(roomId, favourite) }

    fun setUnread(unread: Boolean) = act { actions.setUnread(roomId, unread) }

    fun setMuted(muted: Boolean) = act { actions.setMuted(roomId, muted) }

    fun setProtectScreen(enabled: Boolean) = editPrefs { it.copy(protectScreen = enabled) }

    fun setTypingAlerts(enabled: Boolean) = editPrefs { it.copy(typingAlerts = enabled) }

    fun setReadAlerts(enabled: Boolean) = editPrefs { it.copy(readAlerts = enabled) }

    fun leave() = act { actions.leave(roomId).onSuccess { mutableLeft.value = true } }

    fun block(userId: String) = act { blockedUsers.block(userId) }

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

    private fun editPrefs(transform: (RoomPrefs) -> RoomPrefs) {
        viewModelScope.launch {
            try {
                roomPrefs.update(roomId, transform)
            } catch (_: IOException) {
                notify(R.string.timeline_action_failed)
            }
        }
    }

    private fun act(block: suspend () -> Result<Unit>) {
        viewModelScope.launch { block().onFailure { notify(R.string.timeline_action_failed) } }
    }
}

private const val EXTRA_PAGES = 2
