package org.phalanx.android.ui.screens.catchup

import org.junit.Assert.assertEquals
import org.junit.Test
import org.phalanx.android.core.ai.CatchUp
import org.phalanx.android.data.model.RoomSummary

class NeedTest {
    private fun card(id: String, catchUp: CatchUp?) = CatchUpCard(
        room = RoomSummary(
            id = id,
            name = id,
            isDirect = true,
            avatar = null,
            preview = null,
            previewIsOwn = false,
            previewSender = null,
            lastActivityMillis = null,
            unreadCount = 1,
            highlightCount = 0,
            isFavourite = false
        ),
        state = catchUp?.let { CardState.Done(it, "\$e") } ?: CardState.Reading
    )

    private val both = CatchUp(listOf("point"), waiting = "Saturday?", promised = "Send the photos")

    @Test
    fun `needs gather what is waiting, then what was promised, room by room`() {
        val needs = needsOf(listOf(card("a", both), card("b", null), card("c", CatchUp(emptyList(), "Call?", null))))
        assertEquals(
            listOf("a" to NeedKind.Waiting, "a" to NeedKind.Promised, "c" to NeedKind.Waiting),
            needs.map { it.card.room.id to it.kind }
        )
    }

    @Test
    fun `marking done takes one need off and undoing puts it back`() {
        val done = both.without(NeedKind.Waiting)
        assertEquals(null, done.waiting)
        assertEquals("Send the photos", done.promised)
        assertEquals(both, done.with(NeedKind.Waiting, "Saturday?"))
    }
}
