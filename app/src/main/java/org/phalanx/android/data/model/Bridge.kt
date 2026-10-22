package org.phalanx.android.data.model

/**
 * A network a room is bridged to. [localparts] are how its bridge names the users it brings over
 * (mautrix's defaults, which most servers keep): "@whatsapp_…", "@discord_…", and its bot.
 */
enum class Bridge(internal vararg val localparts: String) {
    WhatsApp("whatsapp"),
    Signal("signal"),
    Telegram("telegram"),
    Discord("discord"),
    Instagram("instagram"),
    Messenger("facebook", "messenger"),

    /** mautrix-meta left on its default names, which don't say Instagram or Messenger. */
    Meta("meta"),
    Slack("slack"),
    GoogleMessages("gmessages"),
    LinkedIn("linkedin"),
    Twitter("twitter"),
    Bluesky("bluesky"),
    IMessage("imessage")
}
