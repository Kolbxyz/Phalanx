package org.phalanx.android.ui.screens.devices

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.phalanx.android.data.model.AccountException
import org.phalanx.android.data.model.AccountFailure
import org.phalanx.android.data.model.Device
import org.phalanx.android.data.repository.AccountRepository

@Immutable
data class DevicesUiState(
    /** Null while loading. */
    val devices: List<Device>? = null,
    val loadFailed: Boolean = false,
    /** The device whose sign-out dialog is open. */
    val removing: Device? = null,
    val password: String = "",
    val busy: Boolean = false,
    val wrongPassword: Boolean = false,
    val removeFailed: Boolean = false
)

@HiltViewModel
class DevicesViewModel @Inject constructor(private val account: AccountRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(DevicesUiState())
    val state: StateFlow<DevicesUiState> = mutableState.asStateFlow()

    init {
        load()
    }

    fun load() {
        mutableState.update { it.copy(loadFailed = false) }
        viewModelScope.launch {
            account.devices().fold(
                // This device first, then the most recently active.
                onSuccess = { list ->
                    val order = compareByDescending<Device> { it.isCurrent }
                        .thenByDescending { it.lastSeenMillis ?: 0L }
                    val sorted = list.sortedWith(order)
                    mutableState.update { it.copy(devices = sorted) }
                },
                onFailure = { mutableState.update { it.copy(loadFailed = true) } }
            )
        }
    }

    fun askRemove(device: Device) = mutableState.update {
        it.copy(removing = device, password = "", wrongPassword = false, removeFailed = false)
    }

    fun onPassword(value: String) = mutableState.update {
        it.copy(password = value, wrongPassword = false, removeFailed = false)
    }

    fun dismissRemove() {
        if (mutableState.value.busy) return
        mutableState.update { it.copy(removing = null, password = "") }
    }

    fun remove() {
        val current = mutableState.value
        val device = current.removing ?: return
        if (current.password.isEmpty() || current.busy) return
        mutableState.update { it.copy(busy = true, wrongPassword = false, removeFailed = false) }
        viewModelScope.launch {
            account.deleteDevice(device.id, current.password).fold(
                onSuccess = {
                    mutableState.update { state ->
                        state.copy(
                            busy = false,
                            removing = null,
                            password = "",
                            devices = state.devices?.filterNot { it.id == device.id }
                        )
                    }
                },
                onFailure = { error ->
                    val wrong = (error as? AccountException)?.failure == AccountFailure.WrongPassword
                    mutableState.update { it.copy(busy = false, wrongPassword = wrong, removeFailed = !wrong) }
                }
            )
        }
    }
}
