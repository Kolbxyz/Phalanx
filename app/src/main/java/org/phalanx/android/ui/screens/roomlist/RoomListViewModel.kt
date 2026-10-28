package org.phalanx.android.ui.screens.roomlist

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.phalanx.android.core.contacts.foldPeople
import org.phalanx.android.core.prefs.SettingsStore
import org.phalanx.android.data.model.PeopleLinks
import org.phalanx.android.data.model.RoomSummary
import org.phalanx.android.data.repository.AccountProfile
import org.phalanx.android.data.repository.AccountRepository
import org.phalanx.android.data.repository.DraftsRepository
import org.phalanx.android.data.repository.PeopleRepository
import org.phalanx.android.data.repository.ReminderRepository
import org.phalanx.android.data.repository.RoomActionsRepository
import org.phalanx.android.data.repository.RoomListRepository
import org.phalanx.android.ui.components.decodeAvatar

@HiltViewModel
class RoomListViewModel @Inject constructor(
    roomList: RoomListRepository,
    account: AccountRepository,
    drafts: DraftsRepository,
    private val actions: RoomActionsRepository,
    settings: SettingsStore,
    reminders: ReminderRepository,
    people: PeopleRepository
) : ViewModel() {
    /** How many reminders are still to come, shown in the side panel. */
    val reminderCount: StateFlow<Int> = reminders.reminders.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), 0)

    /** Offered with on-device AI on and something unread to catch up on. */
    val canCatchUp: StateFlow<Boolean> = combine(settings.settings, roomList.rooms) { current, rooms ->
        current.aiSummaries && rooms.orEmpty().any { it.hasUnread && !it.isMuted && !it.isLowPriority }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), false)

    // Starts from the repository's current list, so coming back to this screen never flashes a
    // loading state.
    private val filter = MutableStateFlow(RoomFilter.All)
    private val source = MutableStateFlow<RoomSource>(RoomSource.All)

    val state: StateFlow<RoomListUiState> = combine(roomList.rooms, filter, source, people.links, ::toUiState)
        .flowOn(Dispatchers.Default)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            toUiState(roomList.rooms.value, filter.value, source.value, people.links.value)
        )

    /** Unsent text by room ID, shown in place of the last message. */
    val drafts: StateFlow<Map<String, String>> = drafts.drafts

    init {
        roomList.rooms.filterNotNull().onEach { rooms -> drafts.lookUp(rooms.map { it.id }) }.launchIn(viewModelScope)
    }

    /** For the account button in the top bar. */
    val profile: StateFlow<AccountProfile?> = account.profile

    val avatar: StateFlow<ImageBitmap?> = account.avatar(AVATAR_PX)
        .map(::decodeAvatar)
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** Set when a room action failed; cleared once shown. */
    private val mutableFailed = MutableStateFlow(false)
    val actionFailed: StateFlow<Boolean> = mutableFailed.asStateFlow()

    fun setFavourite(room: RoomSummary, favourite: Boolean) = act(room) { actions.setFavourite(it, favourite) }

    fun setUnread(room: RoomSummary, unread: Boolean) = act(room) { actions.setUnread(it, unread) }

    fun leave(room: RoomSummary) = act(room) { actions.leave(it) }

    fun setMuted(room: RoomSummary, muted: Boolean) = act(room) { actions.setMuted(it, muted) }

    fun setArchived(room: RoomSummary, archived: Boolean) = act(room) { actions.setArchived(it, archived) }

    fun setFilter(value: RoomFilter) {
        filter.value = value
    }

    fun setSource(value: RoomSource) {
        source.value = value
    }

    fun dismissFailure() {
        mutableFailed.value = false
    }

    // A person's row stands for every chat of theirs, so an action on it applies to each.
    private fun act(room: RoomSummary, block: suspend (roomId: String) -> Result<Unit>) {
        val roomIds = (state.value as? RoomListUiState.Loaded)?.roomsOf?.get(room.id) ?: listOf(room.id)
        viewModelScope.launch {
            if (roomIds.map { block(it) }.any { it.isFailure }) mutableFailed.value = true
        }
    }

    private fun toUiState(
        rooms: List<RoomSummary>?,
        filter: RoomFilter,
        source: RoomSource,
        links: PeopleLinks?
    ): RoomListUiState {
        rooms ?: return RoomListUiState.Loading
        val now = System.currentTimeMillis()
        val filtered = rooms.filter { filter.matches(it, now) }
        // Folded within the source, so a network's list still shows that network's chat alone.
        val folded = foldPeople(filtered.filter(source::matches), links ?: PeopleLinks())
        return RoomListUiState.Loaded(
            rooms = folded.rows,
            roomsOf = folded.roomsOf,
            filter = filter,
            archivedCount = rooms.count { it.isArchived(now) },
            source = source,
            sources = sourcesOf(filtered)
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        // 32 dp at up to 3x density.
        const val AVATAR_PX = 96
    }
}
