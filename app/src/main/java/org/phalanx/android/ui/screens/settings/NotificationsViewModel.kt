package org.phalanx.android.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.phalanx.android.data.model.PushDistributor
import org.phalanx.android.data.model.PushStatus
import org.phalanx.android.data.repository.PushRepository
import javax.inject.Inject

/** The gateway dialog's state: closed, open, or open after a rejected address. */
enum class GatewayEditing { Closed, Open, Invalid }

@HiltViewModel
class NotificationsViewModel @Inject constructor(private val push: PushRepository) : ViewModel() {
    val status: StateFlow<PushStatus> = push.status

    /** Null: automatic. */
    val customGateway: StateFlow<String?> = push.customGateway
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    private val mutableDistributors = MutableStateFlow(push.distributors())
    val distributors: StateFlow<List<PushDistributor>> = mutableDistributors.asStateFlow()

    private val mutableEditing = MutableStateFlow(GatewayEditing.Closed)
    val editing: StateFlow<GatewayEditing> = mutableEditing.asStateFlow()

    /** On every return to the page: the user may have just installed or removed a distributor. */
    fun refreshDistributors() {
        mutableDistributors.value = push.distributors()
    }

    fun useDistributor(packageName: String) {
        viewModelScope.launch { push.useDistributor(packageName) }
    }

    fun editGateway() {
        mutableEditing.value = GatewayEditing.Open
    }

    fun dismissGateway() {
        mutableEditing.value = GatewayEditing.Closed
    }

    /** A blank [url] means automatic. */
    fun saveGateway(url: String) {
        viewModelScope.launch {
            val accepted = push.setCustomGateway(url.trim().ifEmpty { null })
            mutableEditing.value = if (accepted) GatewayEditing.Closed else GatewayEditing.Invalid
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
