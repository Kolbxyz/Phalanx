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

/** The user's one-to-one chats as people: linked chats together, every other chat on its own. */
internal fun peopleOf(rooms: List<RoomSummary>, links: PeopleLinks): List<Person> {
    val dms = rooms.filter { it.dmUserId != null }
    val byUser = dms.associateBy { checkNotNull(it.dmUserId) }
    val linked = links.groups.map { group -> group.mapNotNull(byUser::get) }.filter { it.size > 1 }
    val inGroups = linked.flatten().map { it.id }.toSet()
    return linked.map { Person(it.first().name, it) } +
        dms.filter { it.id !in inGroups }.map { Person(it.name, listOf(it)) }
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
