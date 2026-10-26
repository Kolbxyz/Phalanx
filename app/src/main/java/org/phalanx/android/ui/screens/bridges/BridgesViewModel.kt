package org.phalanx.android.ui.screens.bridges

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.phalanx.android.data.model.BridgeHealth
import org.phalanx.android.data.model.BridgeNetwork
import org.phalanx.android.data.model.BridgeStatus
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.model.healthOf
import org.phalanx.android.data.repository.BridgeRepository
import org.phalanx.android.data.repository.RoomListRepository

@HiltViewModel
class BridgesViewModel @Inject constructor(
    private val bridges: BridgeRepository,
    roomList: RoomListRepository
) : ViewModel() {
    /** Null while the homeserver is probed. */
    private val mutableStatuses = MutableStateFlow<List<BridgeStatus>?>(null)
    val statuses: StateFlow<List<BridgeStatus>?> = mutableStatuses.asStateFlow()

    /**
     * How each bridge with a chat stands, from its bot's last message there, kept live: a bot
     * saying it logged out shows at once.
     */
    val health: StateFlow<Map<BridgeNetwork, BridgeHealth>> = combine(statuses, roomList.rooms) { all, rooms ->
        all.orEmpty().filterIsInstance<BridgeStatus.Available>().mapNotNull { status ->
            val room = rooms?.firstOrNull { it.id == status.roomId } ?: return@mapNotNull null
            val said = (room.preview as? MessageBody.Text)?.text?.takeUnless { room.previewIsOwn }
            status.network to healthOf(said)
        }.toMap()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** The network being connected, so its button shows progress and can't be pressed twice. */
    private val mutableConnecting = MutableStateFlow<BridgeNetwork?>(null)
    val connecting: StateFlow<BridgeNetwork?> = mutableConnecting.asStateFlow()

    private val mutableFailed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = mutableFailed.asStateFlow()

    /** The chat to open once the bot was asked to sign in. */
    private val mutableOpen = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val open: SharedFlow<String> = mutableOpen

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { mutableStatuses.value = bridges.statuses() }
    }

    fun connect(status: BridgeStatus.Available) {
        if (mutableConnecting.value != null) return
        mutableConnecting.value = status.network
        mutableFailed.value = false
        viewModelScope.launch {
            bridges.connect(status.network, status.botId).fold(
                onSuccess = {
                    mutableOpen.tryEmit(it)
                    // Back here, the network then offers its chat instead of connecting again.
                    refresh()
                },
                onFailure = { mutableFailed.value = true }
            )
            mutableConnecting.value = null
        }
    }
}
