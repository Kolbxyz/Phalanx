package org.phalanx.android.data.model

/**
 * How a bridge stands, from the last thing its bot said in the chat with it. mautrix bots say so
 * when a login succeeds, ends or breaks; nothing is sent to ask, so checking costs nothing.
 */
fun healthOf(lastBotMessage: String?): BridgeHealth {
    val text = lastBotMessage?.lowercase() ?: return BridgeHealth.Unknown
    return when {
        BROKEN.any { it in text } -> BridgeHealth.LoggedOut
        WORKING.any { it in text } -> BridgeHealth.Connected
        else -> BridgeHealth.Unknown
    }
}

// Checked first: "you're not logged in" contains "logged in".
private val BROKEN = listOf(
    "not logged in",
    "logged out",
    "log in again",
    "login again",
    "disconnected",
    "connection lost",
    "lost connection",
    "session expired",
    "bad_credentials",
    "bad credentials",
    "unknown_error",
    "please re-login",
    "relogin",
    "re-login"
)

private val WORKING = listOf(
    "successfully logged in",
    "logged in as",
    "login successful",
    "connected to",
    "reconnected"
)
