package org.phalanx.android.core.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PushPayloadTest {
    @Test
    fun readsRoomAndEvent() {
        val push = """{"notification":{"event_id":"${'$'}e","room_id":"!r:x.org","counts":{"unread":2}}}"""
        assertEquals(PushedEvent("!r:x.org", "${'$'}e"), parsePush(push.encodeToByteArray()))
    }

    @Test
    fun ignoresCountOnlyPush() {
        assertNull(parsePush("""{"notification":{"counts":{"unread":0}}}""".encodeToByteArray()))
    }

    @Test
    fun ignoresNonMatrixBytes() {
        assertNull(parsePush("not json".encodeToByteArray()))
        assertNull(parsePush("""{"other":1}""".encodeToByteArray()))
    }

    @Test
    fun recognisesMatrixGateway() {
        assertTrue(GatewayDiscovery.parsesAsMatrixGateway("""{"unifiedpush":{"gateway":"matrix"}}"""))
        assertFalse(GatewayDiscovery.parsesAsMatrixGateway("""{"unifiedpush":{"gateway":"other"}}"""))
        assertFalse(GatewayDiscovery.parsesAsMatrixGateway("""{"unifiedpush":"matrix"}"""))
        assertFalse(GatewayDiscovery.parsesAsMatrixGateway("<html>"))
    }
}
