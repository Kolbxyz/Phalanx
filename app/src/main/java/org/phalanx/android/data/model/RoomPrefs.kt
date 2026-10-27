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
    val readAlerts: Boolean = false,
    /**
     * The offline translation pair the room's messages are translated with as they come into
     * view, such as "en-fr" (see [org.phalanx.android.core.ai.translation.LanguagePair.key]);
     * null leaves them as they are.
     */
    val translatePair: String? = null
)
