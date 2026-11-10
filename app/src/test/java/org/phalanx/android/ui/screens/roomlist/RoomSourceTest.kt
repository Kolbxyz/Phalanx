package org.phalanx.android.ui.screens.roomlist

import org.junit.Assert.assertEquals
import org.junit.Test
import org.phalanx.android.data.model.Bridge
import org.phalanx.android.data.model.RoomSummary

class RoomSourceTest {
    private fun room(id: String, bridge: Bridge?, unread: Long, muted: Boolean = false) = RoomSummary(
        id = id,
        name = id,
        isDirect = false,
        avatar = null,
        preview = null,
        previewIsOwn = false,
        previewSender = null,
        lastActivityMillis = null,
        unreadCount = unread,
        highlightCount = 0,
        isFavourite = false,
        bridge = bridge,
        isMuted = muted
    )

    @Test
    fun `tabs count unread messages, leaving muted rooms out`() {
        val rooms = listOf(
            room("a", Bridge.WhatsApp, unread = 3),
            room("b", Bridge.WhatsApp, unread = 12, muted = true),
            room("c", null, unread = 2),
            room("d", Bridge.Telegram, unread = 0)
        )
        val tabs = sourcesOf(rooms).associateBy { it.source }
        assertEquals(5L, tabs.getValue(RoomSource.All).unread)
        assertEquals(2L, tabs.getValue(RoomSource.Matrix).unread)
        assertEquals(3L, tabs.getValue(RoomSource.Network(Bridge.WhatsApp)).unread)
        assertEquals(2, tabs.getValue(RoomSource.Network(Bridge.WhatsApp)).count)
    }

    @Test
    fun `All and Matrix lead, then networks busiest first`() {
        val rooms = listOf(
            room("a", Bridge.Telegram, 0),
            room("b", Bridge.WhatsApp, 0),
            room("c", Bridge.WhatsApp, 0)
        )
        assertEquals(
            listOf(
                RoomSource.All,
                RoomSource.Matrix,
                RoomSource.Network(Bridge.WhatsApp),
                RoomSource.Network(Bridge.Telegram)
            ),
            sourcesOf(rooms).map { it.source }
        )
    }
}
