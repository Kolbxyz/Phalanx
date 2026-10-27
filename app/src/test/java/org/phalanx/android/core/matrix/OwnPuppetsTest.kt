package org.phalanx.android.core.matrix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.phalanx.android.data.model.RoomSummary

class OwnPuppetsTest {
    private fun room(id: String, dmUserId: String? = null, vararg candidates: String) = RoomSummary(
        id = id,
        name = id,
        isDirect = false,
        avatar = null,
        preview = null,
        previewIsOwn = false,
        previewSender = null,
        lastActivityMillis = null,
        unreadCount = 0,
        highlightCount = 0,
        isFavourite = false,
        dmUserId = dmUserId,
        dmCandidates = candidates.toList()
    )

    @Test
    fun theAccountInSeveralChatsIsTheUser() {
        val rooms = listOf(
            room("a", null, "@instagram_me:s", "@instagram_ann:s"),
            room("b", null, "@instagram_bob:s", "@instagram_me:s")
        ).resolvingOwnPuppets()

        assertEquals(listOf("@instagram_ann:s", "@instagram_bob:s"), rooms.map { it.dmUserId })
    }

    @Test
    fun aSingleChatCannotTellWhoIsWho() {
        val rooms = listOf(room("a", null, "@instagram_me:s", "@instagram_ann:s")).resolvingOwnPuppets()

        assertNull(rooms.single().dmUserId)
    }

    @Test
    fun aChatWithOnePersonKeepsThem() {
        val solo = room("c", "@signal_x:s", "@signal_x:s")

        assertEquals("@signal_x:s", listOf(solo).resolvingOwnPuppets().single().dmUserId)
    }

    @Test
    fun anAccountNamedAsThePersonInChatsOfDifferentNamesIsNotThem() {
        val rooms = listOf(
            room("a", "@instagram_me:s", "@instagram_me:s"),
            room("b", "@instagram_me:s", "@instagram_me:s")
        ).mapIndexed { i, r -> r.copy(name = "Chat $i") }.resolvingOwnPuppets()

        assertEquals(listOf(null, null), rooms.map { it.dmUserId })
    }
}
