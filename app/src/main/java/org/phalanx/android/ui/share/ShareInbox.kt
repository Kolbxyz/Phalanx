package org.phalanx.android.ui.share

import android.net.Uri
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What another app shared to Phalanx: text, files, or both. */
data class SharedContent(val text: String?, val uris: List<Uri>)

/**
 * Holds a share from another app while the user picks a conversation, then until that room's
 * composer takes it. In memory only: the read grant on the shared files ends with the activity.
 */
@Singleton
class ShareInbox @Inject constructor() {
    private val mutablePending = MutableStateFlow<SharedContent?>(null)

    /** A share waiting for its conversation to be picked. */
    val pending: StateFlow<SharedContent?> = mutablePending

    private val targeted = mutableMapOf<String, SharedContent>()

    fun offer(content: SharedContent) {
        mutablePending.value = content
    }

    fun dismiss() {
        mutablePending.value = null
    }

    /** Hands the pending share to [roomId], for its composer to [take] once open. */
    fun sendTo(roomId: String) {
        val content = mutablePending.value ?: return
        synchronized(targeted) { targeted[roomId] = content }
        mutablePending.value = null
    }

    fun take(roomId: String): SharedContent? = synchronized(targeted) { targeted.remove(roomId) }
}
