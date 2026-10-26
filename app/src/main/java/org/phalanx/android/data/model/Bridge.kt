package org.phalanx.android.data.model

/**
 * A chat network the homeserver may bridge, through a mautrix bridge bot. [botLocalparts] are the
 * bot names deployments commonly use, tried in order; [loginCommand] starts the bot's sign-in.
 */
enum class BridgeNetwork(val botLocalparts: List<String>, val loginCommand: String) {
    WhatsApp(listOf("whatsappbot"), "login qr"),
    Discord(listOf("discordbot"), "login-qr"),
    Instagram(listOf("instagrambot", "metabot"), "login"),
    Messenger(listOf("messengerbot", "facebookbot", "metabot"), "login"),
    Telegram(listOf("telegrambot"), "login"),
    Signal(listOf("signalbot"), "login"),
}

/** Where a network stands for this account. */
sealed interface BridgeStatus {
    val network: BridgeNetwork

    /** The homeserver runs no bot for it under a known name. */
    data class Unavailable(override val network: BridgeNetwork) : BridgeStatus

    /** The bot exists; [roomId] is the chat with it once one was started. */
    data class Available(override val network: BridgeNetwork, val botId: String, val roomId: String?) : BridgeStatus
}
