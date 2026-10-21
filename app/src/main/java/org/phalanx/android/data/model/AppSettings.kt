package org.phalanx.android.data.model

enum class ThemeMode { System, Light, Dark }

/** App-wide preferences. The defaults are what a fresh install gets, and lean private. */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = true,
    val amoledBlack: Boolean = false,
    /** Forces reduced motion on; the phone's "Remove animations" setting applies either way. */
    val reduceMotion: Boolean = false,
    /** Off: read receipts stay private, so others never see when this account read their messages. */
    val publicReadReceipts: Boolean = false
)
