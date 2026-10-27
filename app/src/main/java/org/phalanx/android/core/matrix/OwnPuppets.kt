package org.phalanx.android.core.matrix

import org.phalanx.android.data.model.RoomSummary

/**
 * A bridge may bring the user's own account into every chat as a puppet, next to the person they
 * talk to. That puppet is the one who sits in several of these chats; the other is who the chat is
 * with. A chat where that cannot be told stays without a person, rather than with the wrong one.
 */
internal fun List<RoomSummary>.resolvingOwnPuppets(): List<RoomSummary> {
    val own = ownPuppets()
    if (own.isEmpty()) return this
    return map { room ->
        val theirs = room.dmCandidates.filter { it !in own }.singleOrNull()
        when {
            room.dmUserId == null && room.dmCandidates.size == 2 -> room.copy(dmUserId = theirs)
            room.dmUserId in own -> room.copy(dmUserId = theirs)
            else -> room
        }
    }
}

private fun List<RoomSummary>.ownPuppets(): Set<String> {
    val inPairs = filter { it.dmUserId == null && it.dmCandidates.size == 2 }
        .flatMap { it.dmCandidates }
        .groupingBy { it }
        .eachCount()
        .filterValues { it >= MIN_CHATS }
        .keys
    // One account named as "the other person" in chats of different names is the user's own.
    val named = mapNotNull { room -> room.dmUserId?.let { it to room.name.lowercase() } }
        .groupBy({ it.first }, { it.second })
        .filterValues { names -> names.distinct().size >= MIN_CHATS }
        .keys
    return inPairs + named
}

// One puppet in a single chat could just be a person met twice.
private const val MIN_CHATS = 2
