package org.phalanx.android.core.matrix

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * The mute choices made on this device, by room. The SDK's cached notification mode on a room only
 * refreshes on its next room info update, so without this a toggle shows the old state and a
 * notification can still slip through in between.
 */
@Singleton
internal class MuteStates @Inject constructor() {
    private val mutable = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    val byRoom: StateFlow<Map<String, Boolean>> = mutable

    fun set(roomId: String, muted: Boolean) = mutable.update { it + (roomId to muted) }

    fun forget(roomId: String) = mutable.update { it - roomId }
}
