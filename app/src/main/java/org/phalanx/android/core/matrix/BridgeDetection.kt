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

/** A room member by Matrix ID, with the avatar a one-to-one chat borrows. */
internal class RoomPerson(val userId: String, val avatarUrl: String?)

/**
 * The people a one-to-one chat could be with: the heroes but for bridge bots and service members.
 * A bridged chat counts too, since bridges rarely mark their rooms as direct. It holds two when the
 * user's own account is bridged into the room as well; [resolvingOwnPuppets] tells them apart.
 */
internal fun RoomInfo.people(heroes: List<RoomPerson>): List<RoomPerson> {
    val oneToOne = isDirect || (bridge(heroes) != null && joinedMembersCount.toLong() <= BRIDGED_DM_MEMBERS)
    return if (oneToOne) heroes.filter { it.userId !in serviceMembers && !isBridgeBot(it.userId) } else emptyList()
}

// The user, their own bridged account, the person brought over and the bridge's bot.
private const val BRIDGED_DM_MEMBERS = 4L

/**
 * The heroes are the few members a room is summed up by; bridged rooms list puppets there. A
 * server leaves them out of a room that has a name, as bridges give theirs, so a small room's
 * members stand in.
 */
internal fun RoomInfo.bridge(
    heroes: List<RoomPerson> = this.heroes.map { RoomPerson(it.userId, it.avatarUrl) }
): Bridge? =
    bridgeOf(heroes.map { it.userId } + serviceMembers)
