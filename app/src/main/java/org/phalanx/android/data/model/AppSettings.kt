package org.phalanx.android.data.model

enum class ThemeMode { System, Light, Dark }

/** How large message text is drawn in conversations, relative to the system font size. */
enum class TextSize(val scale: Float) { Small(SMALL_SCALE), Default(1f), Large(LARGE_SCALE) }

/** How long the app may stay out of sight before it asks to be unlocked again. */
enum class LockTimeout(val millis: Long) {
    Immediately(0),
    OneMinute(ONE_MINUTE_MILLIS),
    FiveMinutes(FIVE_MINUTES_MILLIS),
    FifteenMinutes(FIFTEEN_MINUTES_MILLIS)
}

/** How much a notification shows: the sender and message, the sender only, or neither. */
enum class NotificationContent { Full, NameOnly, Hidden }

private const val ONE_MINUTE_MILLIS = 60_000L
private const val FIVE_MINUTES_MILLIS = 300_000L
private const val FIFTEEN_MINUTES_MILLIS = 900_000L

private const val SMALL_SCALE = 0.9f
private const val LARGE_SCALE = 1.15f

/** App-wide preferences. The defaults are what a fresh install gets, and lean private. */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = true,
    val amoledBlack: Boolean = false,
    /** Forces reduced motion on; the phone's "Remove animations" setting applies either way. */
    val reduceMotion: Boolean = false,
    val messageTextSize: TextSize = TextSize.Default,
    /** On: the keyboard's Enter key sends; a new line then needs Shift+Enter on a hardware keyboard. */
    val enterToSend: Boolean = false,
    /** On: *stars*, `code` and lists in a message are sent formatted. */
    val markdown: Boolean = true,
    /** Off: photos are scaled down before upload. Location data is stripped either way. */
    val fullQualityImages: Boolean = false,
    /** Off: photos and video previews in conversations wait for a tap before downloading. */
    val autoLoadMedia: Boolean = true,
    /** Off: others never see the typing dots for this account. Their own still show here. */
    val sendTypingNotices: Boolean = false,
    /** Off: read receipts stay private, so others never see when this account read their messages. */
    val publicReadReceipts: Boolean = false,
    /** On: Emoji Kitchen mixes are offered; each one picked is downloaded from Google's servers. */
    val emojiKitchen: Boolean = false,
    /** On: an edited message offers its earlier versions, fetched from the server when asked. */
    val showEditHistory: Boolean = false,
    /**
     * On: every message received is copied for 7 days, sealed on this phone, so one deleted in
     * that time can still be read. Turning it off erases the copies.
     */
    val keepDeletedMessages: Boolean = false,
    /** On: a conversation can be summed up by a language model that runs on this phone only. */
    val aiSummaries: Boolean = false,
    /**
     * On: what the AI writes (transcripts, for now) is kept sealed on this phone, so it is not
     * written again. Turning it off, or signing out, deletes it.
     */
    val aiOnDisk: Boolean = false,
    /** On: voice messages are written out as they come into view, once a speech model is imported. */
    val autoTranscribe: Boolean = false,
    /**
     * On, with [aiOnDisk]: photos in the user's rooms are described while the phone charges and
     * sits idle, so search finds them by what they show. Turning it off erases the descriptions.
     */
    val photoSearch: Boolean = false,
    /** On: opening the app asks for a fingerprint, face or the phone's PIN. */
    val appLock: Boolean = false,
    val lockTimeout: LockTimeout = LockTimeout.OneMinute,
    /** On: no screenshot or screen recording of the app, and nothing of it in Recents. */
    val blockScreenshots: Boolean = false,
    /** On: the keyboard is asked not to learn from, or suggest from, what is typed here. */
    val incognitoKeyboard: Boolean = false,
    /**
     * On: someone reacting to one of this account's messages notifies. It turns on the account's
     * reaction push rule, which other apps signed in to the account may then follow too.
     */
    val reactionNotifications: Boolean = false,
    val notificationContent: NotificationContent = NotificationContent.Full,
    /** The Matrix push gateway the user chose. Null: the distributor's own, else the public one. */
    val pushGateway: String? = null
)
