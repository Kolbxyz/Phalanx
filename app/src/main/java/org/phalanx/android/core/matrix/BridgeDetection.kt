package org.phalanx.android.core.matrix

import org.phalanx.android.data.model.Bridge

/**
 * The network bridging a room, read from its members' Matrix IDs: a bridged user is "@whatsapp_…"
 * and the bridge's bot "@whatsappbot". Null for a room of Matrix users only.
 */
internal fun bridgeOf(userIds: List<String>): Bridge? = userIds.firstNotNullOfOrNull { userId ->
    val localpart = userId.removePrefix("@").substringBefore(':')
    Bridge.entries.firstOrNull { bridge ->
        bridge.localparts.any { prefix ->
            localpart.startsWith(prefix) && localpart.removePrefix(prefix).let { it.startsWith('_') || it == "bot" }
        }
    }
}
