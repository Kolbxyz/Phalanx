package org.phalanx.android.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phalanx.android.data.model.Member

class OutgoingMessageTest {
    @Test
    fun `silent messages are notices without the prefix`() {
        val message = OutgoingMessage.of("@silent **late** night", emptyList(), formatting = true)
        assertEquals(OutgoingMessage.Kind.Notice, message.kind)
        assertEquals("**late** night", message.body)
        assertEquals("<strong>late</strong> night", message.html)
    }

    @Test
    fun `emotes drop the command`() {
        val message = OutgoingMessage.of("/me waves", emptyList(), formatting = true)
        assertEquals(OutgoingMessage.Kind.Emote, message.kind)
        assertEquals("waves", message.body)
        assertNull(message.html)
    }

    @Test
    fun `lists mentioned users and the room`() {
        val bob = Member("@discord_2:s.org", "Bob", null)
        val message = OutgoingMessage.of("@room and @Bob", listOf(bob), formatting = false)
        assertEquals(listOf("@discord_2:s.org"), message.userIds)
        assertTrue(message.mentionsRoom)
    }
}
