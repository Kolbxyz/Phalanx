package org.phalanx.android.core.matrix

import org.matrix.rustcomponents.sdk.RoomHero
import org.matrix.rustcomponents.sdk.RoomInfo
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

/** A bridge's own bot, such as "@whatsappbot", rather than a person it brings over. */
internal fun isBridgeBot(userId: String): Boolean {
    val localpart = userId.removePrefix("@").substringBefore(':')
    return Bridge.entries.any { bridge -> bridge.localparts.any { localpart == it + "bot" } }
}

/**
 * The one other person in a one-to-one chat, or null. A bridged chat counts too: bridges rarely
 * mark their rooms as direct, and they add their bot, which is not a person.
 */
internal fun RoomInfo.otherPerson(): RoomHero? {
    val people = heroes.filter { it.userId !in serviceMembers && !isBridgeBot(it.userId) }
    val oneToOne = isDirect || (bridge() != null && joinedMembersCount.toLong() <= BRIDGED_DM_MEMBERS)
    return people.singleOrNull()?.takeIf { oneToOne }
}

// The user, the person brought over and the bridge's bot.
private const val BRIDGED_DM_MEMBERS = 3L

/** The heroes are the few members a room is summed up by; bridged rooms list puppets there. */
internal fun RoomInfo.bridge(): Bridge? = bridgeOf(heroes.map { it.userId } + serviceMembers)
