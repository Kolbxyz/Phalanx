package org.phalanx.android.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Which chats the user said are one conversation: each group holds the link keys of its chats
 * across networks, the person's Matrix ID for a one-to-one chat and the room ID for any other.
 * [dismissed] are the suggested pairs the user said are not, as [pairKey]s.
 */
@Immutable
@Serializable
data class PeopleLinks(val groups: List<Set<String>> = emptyList(), val dismissed: Set<String> = emptySet())

/** One person, or one group, and their chats: one per network they were linked on. */
@Immutable
data class Person(val name: String, val chats: List<RoomSummary>)

/** Two one-to-one chats that look like the same person on two networks; nothing is linked until confirmed. */
@Immutable
data class LinkSuggestion(val first: RoomSummary, val second: RoomSummary, val reason: LinkReason)

enum class LinkReason { SameName, SamePhone }

/** The same key for a pair of Matrix IDs, whichever comes first. */
fun pairKey(a: String, b: String): String = if (a < b) "$a|$b" else "$b|$a"
