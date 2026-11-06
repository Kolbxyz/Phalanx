package org.phalanx.android.data.model

/**
 * A network a room is bridged to. [localparts] are how its bridge names the users it brings over
 * (mautrix's defaults, which most servers keep): "@whatsapp_…", "@discord_…", and its bot.
 * [messageLimit] is the longest text message the network takes, in characters; null when the
 * bridge copes with any length itself or the network has no limit worth splitting for.
 */
enum class Bridge(internal val messageLimit: Int?, internal vararg val localparts: String) {
    WhatsApp(WHATSAPP_LIMIT, "whatsapp"),

    /** mautrix-signal sends a long message as Signal's own long-text attachment. */
    Signal(null, "signal"),
    Telegram(TELEGRAM_LIMIT, "telegram"),

    /** Without Nitro; mautrix-discord refuses a longer message outright. */
    Discord(DISCORD_LIMIT, "discord"),
    Instagram(INSTAGRAM_LIMIT, "instagram"),

    /** What Meta documents for its Send API; the app may take more, so this errs short. */
    Messenger(MESSENGER_LIMIT, "facebook", "messenger"),

    /** mautrix-meta left on its default names, which don't say Instagram or Messenger: the lower. */
    Meta(META_LIMIT, "meta"),

    /** Slack cuts a message past 40,000 characters, and mautrix-slack then reports it too long. */
    Slack(SLACK_LIMIT, "slack"),

    /** SMS is split by the carrier and RCS takes long messages. */
    GoogleMessages(null, "gmessages"),
    LinkedIn(LINKEDIN_LIMIT, "linkedin"),
    Twitter(TWITTER_LIMIT, "twitter"),

    /** 1,000 graphemes; counting UTF-16 units instead only errs short. */
    Bluesky(BLUESKY_LIMIT, "bluesky"),
    IMessage(null, "imessage")
}

private const val WHATSAPP_LIMIT = 65_536
private const val TELEGRAM_LIMIT = 4_096
private const val DISCORD_LIMIT = 2_000
private const val INSTAGRAM_LIMIT = 1_000
private const val MESSENGER_LIMIT = 2_000
private const val META_LIMIT = 1_000
private const val SLACK_LIMIT = 40_000
private const val LINKEDIN_LIMIT = 8_000
private const val TWITTER_LIMIT = 10_000
private const val BLUESKY_LIMIT = 1_000

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

/** How a bridge's login stands, as its bot last said. */
enum class BridgeHealth {
    Connected,

    /** Logged out, disconnected or its session expired: it needs signing in again. */
    LoggedOut,

    /** The bot said nothing telling. */
    Unknown
}
