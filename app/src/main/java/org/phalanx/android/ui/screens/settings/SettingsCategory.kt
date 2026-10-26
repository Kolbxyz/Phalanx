package org.phalanx.android.ui.screens.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import org.phalanx.android.R

/** The pages of settings, in the order the main page lists them. [icon] is a Material Symbols drawable. */
enum class SettingsCategory(@DrawableRes val icon: Int, @StringRes val title: Int, @StringRes val summary: Int) {
    Security(R.drawable.sym_key, R.string.settings_security, R.string.settings_security_summary),
    Notifications(
        R.drawable.sym_notifications,
        R.string.settings_notifications,
        R.string.settings_notifications_summary
    ),
    Appearance(R.drawable.sym_palette, R.string.settings_appearance, R.string.settings_appearance_summary),
    Conversations(R.drawable.sym_chat, R.string.settings_conversations, R.string.settings_conversations_summary),
    Media(R.drawable.sym_perm_media, R.string.settings_media, R.string.settings_media_summary),
    Privacy(R.drawable.sym_shield, R.string.settings_privacy, R.string.settings_privacy_summary),
    Ai(R.drawable.sym_auto_awesome, R.string.settings_ai, R.string.settings_ai_summary),
    About(R.drawable.sym_info, R.string.settings_about, R.string.settings_about_summary)
}
