package org.phalanx.android.ui.screens.details

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.phalanx.android.R
import org.phalanx.android.data.model.RoomPrefs
import org.phalanx.android.ui.components.SegmentedSection
import org.phalanx.android.ui.screens.settings.SwitchGroup
import org.phalanx.android.ui.screens.settings.SwitchSetting
import org.phalanx.android.ui.theme.Symbols

/** Choices kept on this phone for the room: no screenshots, and alerts while the app is open. */
@Composable
internal fun DetailsPrivacySection(prefs: RoomPrefs, viewModel: RoomDetailsViewModel) {
    SegmentedSection(stringResource(R.string.details_privacy_alerts)) {
        SwitchGroup(
            listOf(
                SwitchSetting(
                    Symbols.NoPhotography, R.string.details_protect_screen,
                    R.string.details_protect_screen_summary, prefs.protectScreen, viewModel::setProtectScreen
                ),
                SwitchSetting(
                    Symbols.Keyboard, R.string.details_typing_alerts,
                    R.string.details_typing_alerts_summary, prefs.typingAlerts, viewModel::setTypingAlerts
                ),
                SwitchSetting(
                    Symbols.Visibility, R.string.details_read_alerts,
                    R.string.details_read_alerts_summary, prefs.readAlerts, viewModel::setReadAlerts
                )
            )
        )
    }
}
