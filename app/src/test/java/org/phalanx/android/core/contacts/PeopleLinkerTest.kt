package org.phalanx.android.core.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phalanx.android.data.model.Bridge
import org.phalanx.android.data.model.LinkReason
import org.phalanx.android.data.model.PeopleLinks
import org.phalanx.android.data.model.RoomSummary
import org.phalanx.android.data.model.pairKey

class PeopleLinkerTest {
    private fun dm(id: String, name: String, userId: String, bridge: Bridge?) = RoomSummary(
        id = id,
        name = name,
        isDirect = true,
        avatar = null,
        preview = null,
        previewIsOwn = false,
        previewSender = null,
        lastActivityMillis = null,
        unreadCount = 0,
        highlightCount = 0,
        isFavourite = false,
        dmUserId = userId,
        bridge = bridge
    )

    private val whatsapp = dm("!a", "Léa Martin (WhatsApp)", "@whatsapp_33612345678:example.org", Bridge.WhatsApp)
    private val signal = dm("!b", "lea  martin", "@signal_5f1c2d:example.org", Bridge.Signal)
    private val matrix = dm("!c", "Léa Martin", "@lea:example.org", null)
    private val sms = dm("!d", "Maman", "@gmessages_0612345678:example.org", Bridge.GoogleMessages)

    @Test
    fun `names match without case, accents or a network tag`() {
        assertEquals("lea martin", nameKey("Léa  Martin (WhatsApp)"))
        assertEquals("lea martin [work]", nameKey("Léa Martin [Work]"))
    }

    @Test
    fun `the same name on two networks is suggested once per pair`() {
        val suggestions = suggestLinks(listOf(whatsapp, signal, matrix), PeopleLinks())
        assertEquals(3, suggestions.size)
        assertTrue(suggestions.all { it.reason == LinkReason.SameName })
    }

    @Test
    fun `the same phone number is found without its country code`() {
        val suggestions = suggestLinks(listOf(whatsapp, sms), PeopleLinks())
        assertEquals(LinkReason.SamePhone, suggestions.single().reason)
    }

    @Test
    fun `a telegram ID is not a phone number`() {
        val telegram = dm("!e", "x", "@telegram_612345678:example.org", Bridge.Telegram)
        assertNull(phoneOf(telegram))
    }

    @Test
    fun `two chats on one network are never suggested`() {
        val other = dm("!f", "Léa Martin", "@whatsapp_33700000000:example.org", Bridge.WhatsApp)
        assertTrue(suggestLinks(listOf(whatsapp, other), PeopleLinks()).isEmpty())
    }

    @Test
    fun `linked and dismissed pairs are left out`() {
        val links = PeopleLinks(
            groups = listOf(setOf(whatsapp.dmUserId!!, signal.dmUserId!!)),
            dismissed = setOf(pairKey(matrix.dmUserId!!, whatsapp.dmUserId!!))
        )
        val suggestions = suggestLinks(listOf(whatsapp, signal, matrix), links)
        assertEquals(setOf(signal.id, matrix.id), suggestions.single().let { setOf(it.first.id, it.second.id) })
    }

    @Test
    fun `linked chats make one person`() {
        val links = PeopleLinks(groups = listOf(setOf(whatsapp.dmUserId!!, signal.dmUserId!!)))
        val people = peopleOf(listOf(whatsapp, signal, matrix), links)
        assertEquals(listOf(2, 1), people.map { it.chats.size })
    }

    @Test
    fun `a linked person is one row, where their most recent chat was, counting every chat's unread`() {
        val links = PeopleLinks(groups = listOf(setOf(whatsapp.dmUserId!!, signal.dmUserId!!)))
        val rooms = listOf(
            sms,
            signal.copy(unreadCount = 2),
            matrix,
            whatsapp.copy(unreadCount = 3, highlightCount = 1)
        )
        val folded = foldPeople(rooms, links)
        assertEquals(listOf(sms.id, signal.id, matrix.id), folded.rows.map { it.id })
        assertEquals(5L, folded.rows[1].unreadCount)
        assertEquals(1L, folded.rows[1].highlightCount)
        assertEquals(setOf(whatsapp.id, signal.id), folded.roomsOf.getValue(signal.id).toSet())
    }

    @Test
    fun `a person with one chat left in the list is not folded`() {
        val links = PeopleLinks(groups = listOf(setOf(whatsapp.dmUserId!!, signal.dmUserId!!)))
        val folded = foldPeople(listOf(signal, matrix), links)
        assertEquals(listOf(signal.id, matrix.id), folded.rows.map { it.id })
        assertTrue(folded.roomsOf.isEmpty())
    }

    @Test
    fun `group chats link by room, however many people are in them`() {
        val discord = dm("!g1", "Climbing crew", "", Bridge.Discord).copy(isDirect = false, dmUserId = null)
        val messenger = dm("!g2", "Climbing", "", Bridge.Messenger).copy(isDirect = false, dmUserId = null)
        val links = PeopleLinks(groups = listOf(setOf(linkKey(discord), linkKey(messenger))))
        assertEquals("!g1", linkKey(discord))
        val people = peopleOf(listOf(discord, messenger, whatsapp), links)
        assertEquals(setOf(discord.id, messenger.id), people.first().chats.map { it.id }.toSet())
    }

    @Test
    fun `a chat linked by its room still counts once who it is with is known`() {
        val links = PeopleLinks(groups = listOf(setOf(whatsapp.id, signal.dmUserId!!)))
        assertEquals(listOf(2, 1), peopleOf(listOf(whatsapp, signal, matrix), links).map { it.chats.size })
    }
}
