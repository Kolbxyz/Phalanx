package org.phalanx.android.data.model

import kotlinx.serialization.Serializable

/** What the user chose on this phone for one room. All off by default. */
@Serializable
data class RoomPrefs(
    /** No screenshot or screen recording while the room is open. */
    val protectScreen: Boolean = false,
    /** A notice when someone starts typing in the room, while Phalanx is open. */
    val typingAlerts: Boolean = false,
    /** A notice when someone reads this account's latest message, while Phalanx is open. */
    val readAlerts: Boolean = false
)
