package org.phalanx.android.ui.screens.room

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.phalanx.android.R
import org.phalanx.android.core.media.AttachmentPreparer
import org.phalanx.android.core.media.VoiceRecorder
import org.phalanx.android.core.prefs.SettingsStore
import org.phalanx.android.data.model.AppSettings
import org.phalanx.android.data.model.MediaRef
import org.phalanx.android.data.model.Member
import org.phalanx.android.data.model.RoomSummary
import org.phalanx.android.data.model.ScheduledMessage
import org.phalanx.android.data.model.KitchenMix
import org.phalanx.android.data.model.Sticker
import org.phalanx.android.data.model.StickerPack
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.data.model.UserTrust
import org.phalanx.android.data.repository.BlockedUsersRepository
import org.phalanx.android.data.repository.MediaRepository
import org.phalanx.android.data.repository.MutedUsersRepository
import org.phalanx.android.data.repository.PushRepository
import org.phalanx.android.data.repository.RoomListRepository
import org.phalanx.android.data.repository.ScheduleRepository
import org.phalanx.android.data.repository.SecurityRepository
import org.phalanx.android.data.repository.StickerRepository
import org.phalanx.android.data.repository.TimelineRepository
import org.phalanx.android.data.repository.TimelineSession
import org.phalanx.android.ui.components.Notice
import org.phalanx.android.ui.components.NoticeKind
import org.phalanx.android.ui.components.OpenRequest
import org.phalanx.android.ui.navigation.RoomRoute
import org.phalanx.android.ui.share.ShareInbox
import javax.inject.Inject

@HiltViewModel
class RoomViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val timelines: TimelineRepository,
    private val media: MediaRepository,
    private val preparer: AttachmentPreparer,
    recorder: VoiceRecorder,
    roomList: RoomListRepository,
    private val mutedUsers: MutedUsersRepository,
    private val push: PushRepository,
    private val blockedUsers: BlockedUsersRepository,
    settingsStore: SettingsStore,
    private val schedules: ScheduleRepository,
    private val security: SecurityRepository,
    private val stickers: StickerRepository,
    private val shares: ShareInbox
) : ViewModel() {
    private val route = savedStateHandle.toRoute<RoomRoute>()
    val roomId: String = route.roomId

    /** From the room list, which is already in memory, so the header shows before the timeline opens. */
    val header: StateFlow<RoomSummary?> = roomList.rooms
        .map { rooms -> rooms?.firstOrNull { it.id == roomId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, roomList.rooms.value?.firstOrNull { it.id == roomId })

    /** Every joined room, for picking where to forward a message. */
    val rooms: StateFlow<List<RoomSummary>?> = roomList.rooms

    val muted: StateFlow<Set<String>> = mutedUsers.muted

    val settings: StateFlow<AppSettings?> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val mutableState = MutableStateFlow<RoomUiState>(RoomUiState.Loading)
    val state: StateFlow<RoomUiState> = mutableState.asStateFlow()

    /** A one-off message for the snackbar; cleared by [dismissNotice]. */
    private val mutableNotice = MutableStateFlow<Notice?>(null)
    val notice: StateFlow<Notice?> = mutableNotice.asStateFlow()

    private val mutableOpen = MutableStateFlow<OpenRequest?>(null)
    val openRequest: StateFlow<OpenRequest?> = mutableOpen.asStateFlow()

    private var session: TimelineSession? = null

    /** The entry to scroll to and highlight, once the one the route asked for has been found. */
    private val mutableFocus = MutableStateFlow<String?>(null)
    val focus: StateFlow<String?> = mutableFocus.asStateFlow()

    /** Who reacted with one key to one message, while that list is open; members null while loading. */
    private val mutableReactors = MutableStateFlow<Reactors?>(null)
    val reactors: StateFlow<Reactors?> = mutableReactors.asStateFlow()

    /** This room's messages waiting on the phone to be sent. */
    val scheduled: StateFlow<List<ScheduledMessage>> = schedules.scheduled(roomId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    internal val composer = ComposerController(viewModelScope, preparer, recorder, schedules, { notify(it) }) {
        settings.value?.sendTypingNotices == true
    }

    init {
        viewModelScope.launch {
            timelines.open(roomId).fold(
                onSuccess = { opened ->
                    session = opened
                    composer.attach(opened)
                    shares.take(roomId)?.let { composer.receive(it.text, it.uris) }
                    observe(opened)
                    // Only on the first opening: back here after process death, the user has moved on.
                    if (route.focusEventId != null && savedStateHandle.get<Boolean>(FOCUSED_KEY) != true) {
                        savedStateHandle[FOCUSED_KEY] = true
                        viewModelScope.launch {
                            val found = opened.seek(route.focusEventId)
                            if (found != null) mutableFocus.value = found else notify(R.string.timeline_focus_not_found)
                        }
                    }
                },
                onFailure = { mutableState.value = RoomUiState.Unavailable }
            )
        }
    }

    /** Where the camera app should write a new photo or video before it is attached. */
    /** While the conversation is on screen, its messages need no notification. */
    fun onShown() = push.startViewing(roomId)

    fun onHidden() = push.stopViewing(roomId)

    fun captureUri(video: Boolean): Uri = preparer.captureUri(video)

    fun toggleReaction(message: TimelineEntry.Message, key: String) = act { it.toggleReaction(message, key) }

    fun redact(message: TimelineEntry.Message) = act { it.redact(message) }

    fun setPinned(message: TimelineEntry.Message, pinned: Boolean) = act { it.setPinned(message, pinned) }

    fun forward(message: TimelineEntry.Message, toRoomId: String) {
        val eventId = message.eventId ?: return
        viewModelScope.launch {
            timelines.forward(roomId, eventId, toRoomId).fold(
                onSuccess = { notify(R.string.timeline_forwarded, NoticeKind.Done) },
                onFailure = { notify(R.string.timeline_action_failed) }
            )
        }
    }

    suspend fun stickerPacks(): List<StickerPack> = stickers.packs()

    fun sendMix(mix: KitchenMix) {
        viewModelScope.launch { stickers.sendKitchen(roomId, mix).onFailure { notify(R.string.composer_send_failed) } }
    }

    fun sendSticker(sticker: Sticker) {
        viewModelScope.launch {
            stickers.send(roomId, sticker).onFailure { notify(R.string.composer_send_failed) }
        }
    }

    fun setMuted(userId: String, muted: Boolean) {
        viewModelScope.launch {
            val result = if (muted) mutedUsers.mute(userId) else mutedUsers.unmute(userId)
            result.fold(
                onSuccess = {
                    notify(if (muted) R.string.timeline_muted else R.string.timeline_unmuted, NoticeKind.Done)
                },
                onFailure = { notify(R.string.timeline_action_failed) }
            )
        }
    }

    /** Opens on [key]'s reactors, with every other reaction to the message a tab away. */
    fun showReactors(message: TimelineEntry.Message, key: String) {
        val current = session ?: return
        if (message.reactions.none { it.key == key }) return
        val opened = Reactors(message.reactions, key, members = null)
        mutableReactors.value = opened
        viewModelScope.launch {
            val members = current.members(message.reactions.flatMap { it.senderIds }.distinct())
            // Unless the user closed it, or opened another, while the names loaded.
            mutableReactors.update { if (it === opened) opened.copy(members = members.associateBy { m -> m.userId }) else it }
        }
    }

    fun dismissReactors() {
        mutableReactors.value = null
    }

    fun scheduleDraft(atMillis: Long) = composer.schedule(roomId, atMillis)

    fun canScheduleExactly(): Boolean = schedules.canScheduleExactly()

    fun sendScheduledNow(id: String) {
        viewModelScope.launch { schedules.sendNow(id) }
    }

    fun cancelScheduled(id: String) {
        viewModelScope.launch { schedules.cancel(id) }
    }

    /** Who [userId] is in this room; just their ID when the room doesn't know them. */
    suspend fun member(userId: String): Member =
        session?.members(listOf(userId))?.firstOrNull() ?: Member(userId, userId, null)

    suspend fun trustOf(userId: String): UserTrust? = security.trustOf(userId)

    fun block(userId: String) {
        viewModelScope.launch { blockedUsers.block(userId).onFailure { notify(R.string.timeline_action_failed) } }
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

    suspend fun fileUri(ref: MediaRef, filename: String, mimeType: String?): Uri? =
        media.openable(ref, filename, mimeType).onFailure { notify(R.string.timeline_open_failed) }.getOrNull()

    fun notify(@StringRes message: Int, kind: NoticeKind = NoticeKind.Error) {
        mutableNotice.value = Notice(message, kind)
    }

    private fun notify(notice: Notice) {
        mutableNotice.value = notice
    }

    fun dismissNotice() {
        mutableNotice.value = null
    }

    fun jumpTo(eventId: String) {
        val current = session ?: return
        viewModelScope.launch {
            val found = current.seek(eventId)
            if (found != null) mutableFocus.value = found else notify(R.string.timeline_focus_not_found)
        }
    }

    fun onFocused() {
        mutableFocus.value = null
    }

    fun loadOlder() {
        val current = session ?: return
        // A failed page is transient; the list asks again the next time the user scrolls to the top.
        viewModelScope.launch { current.loadOlder() }
    }

    override fun onCleared() {
        composer.release()
    }

    private fun act(block: suspend (TimelineSession) -> Result<Unit>) {
        val current = session ?: return
        viewModelScope.launch { block(current).onFailure { notify(R.string.timeline_action_failed) } }
    }

    private fun observe(opened: TimelineSession) {
        viewModelScope.launch {
            combine(opened.entries, opened.hasMoreHistory, opened.typing) { entries, more, typing ->
                RoomUiState.Ready(opened.title, opened.isDirect, entries.asReversed(), more, typing)
            }.collect { mutableState.value = it }
        }
        viewModelScope.launch {
            opened.entries
                .map { entries -> entries.lastOrNull { it is TimelineEntry.Message && !it.isOwn }?.id }
                .filterNotNull()
                .distinctUntilChanged()
                .collect { opened.markRead() }
        }
    }
}

private const val FOCUSED_KEY = "focused"
