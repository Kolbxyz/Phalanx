package org.phalanx.android.data.model

enum class ThemeMode { System, Light, Dark }

/** How large message text is drawn in conversations, relative to the system font size. */
enum class TextSize(val scale: Float) { Small(SMALL_SCALE), Default(1f), Large(LARGE_SCALE) }

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
    val publicReadReceipts: Boolean = false
)
