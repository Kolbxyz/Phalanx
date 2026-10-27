package org.phalanx.android.core.matrix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phalanx.android.data.model.Bridge

class BridgeDetectionTest {
    @Test
    fun `a bridge's bot is not a person`() {
        assertTrue(isBridgeBot("@whatsappbot:example.org"))
        assertTrue(isBridgeBot("@metabot:example.org"))
    }

    @Test
    fun `people brought over by a bridge and Matrix users are not bots`() {
        assertFalse(isBridgeBot("@whatsapp_33612345678:example.org"))
        assertFalse(isBridgeBot("@alice:example.org"))
        assertFalse(isBridgeBot("@robot:example.org"))
    }

    @Test
    fun `the network is read from a bridged user or its bot`() {
        assertEquals(Bridge.Signal, bridgeOf(listOf("@alice:example.org", "@signal_0a1b:example.org")))
        assertEquals(Bridge.Discord, bridgeOf(listOf("@discordbot:example.org")))
        assertNull(bridgeOf(listOf("@alice:example.org", "@whatsappfan:example.org")))
    }
}
