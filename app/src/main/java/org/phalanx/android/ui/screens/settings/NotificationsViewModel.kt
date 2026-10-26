package org.phalanx.android.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.phalanx.android.core.prefs.SettingsStore
import org.phalanx.android.data.model.NotificationContent
import org.phalanx.android.data.model.PushDistributor
import org.phalanx.android.data.model.PushStatus
import org.phalanx.android.data.repository.PushRepository
import org.phalanx.android.data.repository.ReactionPushRepository

/** The gateway dialog's state: closed, open, or open after a rejected address. */
enum class GatewayEditing { Closed, Open, Invalid }

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val push: PushRepository,
    private val settings: SettingsStore,
    private val reactionRule: ReactionPushRepository
) : ViewModel() {
    val status: StateFlow<PushStatus> = push.status

    /** Null: automatic. */
    val customGateway: StateFlow<String?> = push.customGateway
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    private val mutableDistributors = MutableStateFlow(push.distributors())
    val distributors: StateFlow<List<PushDistributor>> = mutableDistributors.asStateFlow()

    private val mutableEditing = MutableStateFlow(GatewayEditing.Closed)
    val editing: StateFlow<GatewayEditing> = mutableEditing.asStateFlow()

    /** Null until the settings are read. */
    val content: StateFlow<NotificationContent?> = settings.settings.map { it.notificationContent }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    fun setContent(content: NotificationContent) {
        viewModelScope.launch { settings.setNotificationContent(content) }
    }

    /** Null until the settings are read. */
    val reactions: StateFlow<Boolean?> = settings.settings.map { it.reactionNotifications }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** Set when the server did not take the change; the switch then stays as it was. */
    private val mutableReactionsFailed = MutableStateFlow(false)
    val reactionsFailed: StateFlow<Boolean> = mutableReactionsFailed.asStateFlow()

    /** The push rule changes first: the switch only moves once the server agreed. */
    fun setReactions(enabled: Boolean) {
        viewModelScope.launch {
            reactionRule.setNotify(enabled).fold(
                onSuccess = {
                    mutableReactionsFailed.value = false
                    settings.setReactionNotifications(enabled)
                },
                onFailure = { mutableReactionsFailed.value = true }
            )
        }
    }

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
