package org.phalanx.android.ui.screens.settings

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PermMedia
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.ui.graphics.vector.ImageVector
import org.phalanx.android.R

/** The pages of settings, in the order the main page lists them. */
enum class SettingsCategory(val icon: ImageVector, @StringRes val title: Int, @StringRes val summary: Int) {
    Security(Icons.Rounded.Key, R.string.settings_security, R.string.settings_security_summary),
    Appearance(Icons.Rounded.Palette, R.string.settings_appearance, R.string.settings_appearance_summary),
    Conversations(
        Icons.AutoMirrored.Rounded.Chat,
        R.string.settings_conversations,
        R.string.settings_conversations_summary
    ),
    Media(Icons.Rounded.PermMedia, R.string.settings_media, R.string.settings_media_summary),
    Privacy(Icons.Rounded.Shield, R.string.settings_privacy, R.string.settings_privacy_summary),
    About(Icons.Rounded.Info, R.string.settings_about, R.string.settings_about_summary)
}
