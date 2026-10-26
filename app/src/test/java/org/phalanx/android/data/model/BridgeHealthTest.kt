package org.phalanx.android.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class BridgeHealthTest {
    @Test
    fun `a broken login is noticed`() = listOf(
        "You're not logged in",
        "Logged out from WhatsApp, please log in again",
        "Disconnected from Signal",
        "Your session expired (BAD_CREDENTIALS)"
    ).forEach { assertEquals(it, BridgeHealth.LoggedOut, healthOf(it)) }

    @Test
    fun `a working login is noticed`() = listOf(
        "Successfully logged in as +33612345678",
        "Logged in as @lea on Discord"
    ).forEach { assertEquals(it, BridgeHealth.Connected, healthOf(it)) }

    @Test
    fun `anything else tells nothing`() {
        assertEquals(BridgeHealth.Unknown, healthOf("Scan the QR code below"))
        assertEquals(BridgeHealth.Unknown, healthOf(null))
    }
}
