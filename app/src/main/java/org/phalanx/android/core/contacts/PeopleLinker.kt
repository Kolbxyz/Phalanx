package org.phalanx.android.core.contacts

import java.text.Normalizer
import org.phalanx.android.data.model.Bridge
import org.phalanx.android.data.model.LinkReason
import org.phalanx.android.data.model.LinkSuggestion
import org.phalanx.android.data.model.PeopleLinks
import org.phalanx.android.data.model.Person
import org.phalanx.android.data.model.RoomSummary
import org.phalanx.android.data.model.pairKey

/**
 * One-to-one chats on two different networks that look like one person: the same name once case,
 * accents and a network tag such as "(WhatsApp)" are set aside, or the same phone number in a
 * bridged ID such as "@whatsapp_33612345678". Pairs already linked or dismissed are left out.
 */
internal fun suggestLinks(rooms: List<RoomSummary>, links: PeopleLinks): List<LinkSuggestion> {
    val dms = rooms.filter { it.dmUserId != null }
    val grouped = links.groups.flatMap { group -> group.map { it to group } }.toMap()
    // Grouped by key first, so a few hundred chats are not compared pair by pair.
    val byPhone = dms.groupBy(::phoneOf).filterKeys { it != null }.values.map { it to LinkReason.SamePhone }
    val byName = dms.groupBy { nameKey(it.name) }.filterKeys { it.length >= MIN_NAME }.values
        .map { it to LinkReason.SameName }
    val seen = HashSet<String>()
    return (byPhone + byName).flatMap { (chats, reason) ->
        chats.flatMapIndexed { index, first -> chats.drop(index + 1).map { Triple(first, it, reason) } }
    }.filter { (first, second) ->
        val a = checkNotNull(first.dmUserId)
        val b = checkNotNull(second.dmUserId)
        first.bridge != second.bridge && grouped[a]?.contains(b) != true &&
            pairKey(a, b) !in links.dismissed && seen.add(pairKey(a, b))
    }.map { (first, second, reason) -> LinkSuggestion(first, second, reason) }
}

/**
 * What a chat is linked by: for a one-to-one chat the person, who outlives the room; for any other
 * chat, a group on Discord or Messenger say, the room itself.
 */
internal fun linkKey(room: RoomSummary): String = room.dmUserId ?: room.id

/** Every key [room] may have been linked by: who a chat is with can be told only after it was linked. */
internal fun linkKeys(room: RoomSummary): Set<String> = setOfNotNull(room.id, room.dmUserId)

/**
 * The user's chats as conversations: chats linked together, however many people each holds, then
 * every other chat on its own.
 */
internal fun peopleOf(rooms: List<RoomSummary>, links: PeopleLinks): List<Person> {
    val byKey = HashMap<String, RoomSummary>()
    rooms.forEach { room -> linkKeys(room).forEach { byKey.putIfAbsent(it, room) } }
    val linked = links.groups.map { group -> group.mapNotNull(byKey::get).distinctBy { it.id } }.filter { it.size > 1 }
    val inGroups = linked.flatten().map { it.id }.toSet()
    return linked.map { Person(it.first().name, it) } +
        rooms.filter { it.id !in inGroups }.map { Person(it.name, listOf(it)) }
}

/** [rooms] with each linked person on one row, and every row's rooms by the row's room ID. */
internal data class FoldedRooms(val rows: List<RoomSummary>, val roomsOf: Map<String, List<String>>)

/**
 * [rooms], most recent first, with each linked person's chats folded into the most recent of them:
 * that row opens their thread, and counts every chat's unread messages. The order is kept.
 */
internal fun foldPeople(rooms: List<RoomSummary>, links: PeopleLinks): FoldedRooms {
    val people = peopleOf(rooms, links).filter { it.chats.size > 1 }
    if (people.isEmpty()) return FoldedRooms(rooms, emptyMap())
    val personOf = people.flatMap { person -> person.chats.map { it.id to person } }.toMap()
    val roomsOf = HashMap<String, List<String>>()
    val rows = rooms.mapNotNull { room ->
        val person = personOf[room.id] ?: return@mapNotNull room
        // The first of a person's chats met is their most recent; the others fold into it.
        if (person.chats.any { it.id in roomsOf }) return@mapNotNull null
        roomsOf[room.id] = person.chats.map { it.id }
        room.copy(
            unreadCount = person.chats.sumOf { it.unreadCount },
            highlightCount = person.chats.sumOf { it.highlightCount },
            isMarkedUnread = person.chats.any { it.isMarkedUnread }
        )
    }
    return FoldedRooms(rows, roomsOf)
}

/** The name without case, accents, a network tag or extra spaces. */
internal fun nameKey(name: String): String {
    val plain = Normalizer.normalize(name, Normalizer.Form.NFD).replace(ACCENTS, "").lowercase()
    val untagged = TAG.replace(plain) { match -> if (match.groupValues[1].trim() in NETWORK_TAGS) "" else match.value }
    return untagged.replace(SPACES, " ").trim()
}

/**
 * The last digits of the phone number in a bridged ID, which survive a missing country code. Only
 * networks whose IDs are phone numbers count: a Telegram ID is digits too, but not a phone.
 */
internal fun phoneOf(room: RoomSummary): String? {
    val localpart = room.dmUserId?.removePrefix("@")?.substringBefore(':') ?: return null
    val digits = localpart.substringAfter('_', "").removePrefix("+")
    val carriesPhone = room.bridge in PHONE_NETWORKS && digits.length >= MIN_PHONE && digits.all(Char::isDigit)
    return if (carriesPhone) digits.takeLast(MIN_PHONE) else null
}

private val ACCENTS = Regex("\\p{Mn}+")
private val TAG = Regex("[(\\[]([^)\\]]*)[)\\]]")
private val SPACES = Regex("\\s+")
private val NETWORK_TAGS =
    Bridge.entries.flatMap { listOf(it.name.lowercase()) + it.localparts }.toSet() + setOf("wa", "sms", "matrix")
private val PHONE_NETWORKS = setOf(Bridge.WhatsApp, Bridge.GoogleMessages, Bridge.IMessage)
private const val MIN_NAME = 3
private const val MIN_PHONE = 9
