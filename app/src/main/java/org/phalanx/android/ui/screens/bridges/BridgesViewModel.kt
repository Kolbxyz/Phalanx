package org.phalanx.android.ui.screens.bridges

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.phalanx.android.data.model.BridgeNetwork
import org.phalanx.android.data.model.BridgeStatus
import org.phalanx.android.data.repository.BridgeRepository

@HiltViewModel
class BridgesViewModel @Inject constructor(private val bridges: BridgeRepository) : ViewModel() {
    /** Null while the homeserver is probed. */
    private val mutableStatuses = MutableStateFlow<List<BridgeStatus>?>(null)
    val statuses: StateFlow<List<BridgeStatus>?> = mutableStatuses.asStateFlow()

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
