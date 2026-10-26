package org.phalanx.android.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Who the user said are one person: each group is the Matrix IDs of that person's one-to-one chats
 * across networks. [dismissed] are the suggested pairs the user said are not, as [pairKey]s.
 */
@Immutable
@Serializable
data class PeopleLinks(val groups: List<Set<String>> = emptyList(), val dismissed: Set<String> = emptySet())

/** One person and their one-to-one chats, one per network they were linked on. */
@Immutable
data class Person(val name: String, val chats: List<RoomSummary>)

/** Two one-to-one chats that look like the same person on two networks; nothing is linked until confirmed. */
@Immutable
data class LinkSuggestion(val first: RoomSummary, val second: RoomSummary, val reason: LinkReason)

enum class LinkReason { SameName, SamePhone }

/** The same key for a pair of Matrix IDs, whichever comes first. */
fun pairKey(a: String, b: String): String = if (a < b) "$a|$b" else "$b|$a"
