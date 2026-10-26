package org.phalanx.android.ui.screens.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.data.model.AppSettings
import org.phalanx.android.data.model.LockTimeout
import org.phalanx.android.ui.components.ConnectedToggleGroup
import org.phalanx.android.ui.components.SegmentGap
import org.phalanx.android.ui.components.SegmentedSection
import org.phalanx.android.ui.screens.lock.canLockApp
import org.phalanx.android.ui.theme.Symbols

/** App lock, screenshot blocking and the incognito keyboard: who can see what is on the screen. */
@Composable
internal fun ScreenPrivacySection(current: AppSettings, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    // App lock falls back to the phone's PIN, so a phone without one can't turn it on.
    val canLock = remember(context) { canLockApp(context) }
    SegmentedSection(stringResource(R.string.settings_screen_privacy)) {
        Column(verticalArrangement = Arrangement.spacedBy(SegmentGap)) {
            SwitchGroup(
                listOf(
                    SwitchSetting(
                        Symbols.Fingerprint, R.string.settings_app_lock,
                        if (canLock) R.string.settings_app_lock_summary else R.string.settings_app_lock_no_screen_lock,
                        current.appLock && canLock
                    ) { if (canLock) viewModel.setAppLock(it) },
                    SwitchSetting(
                        Symbols.NoPhotography, R.string.settings_block_screenshots,
                        R.string.settings_block_screenshots_summary, current.blockScreenshots,
                        viewModel::setBlockScreenshots
                    ),
                    SwitchSetting(
                        Symbols.Incognito, R.string.settings_incognito_keyboard,
                        R.string.settings_incognito_keyboard_summary, current.incognitoKeyboard,
                        viewModel::setIncognitoKeyboard
                    )
                )
            )
        }
        if (current.appLock && canLock) {
            Text(
                text = stringResource(R.string.settings_lock_timeout),
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
            )
            ConnectedToggleGroup(
                options = LockTimeout.entries,
                selected = current.lockTimeout,
                label = { stringResource(it.labelRes()) },
                onSelect = viewModel::setLockTimeout
            )
        }
    }
}

@StringRes
private fun LockTimeout.labelRes(): Int = when (this) {
    LockTimeout.Immediately -> R.string.settings_lock_now
    LockTimeout.OneMinute -> R.string.settings_lock_1_min
    LockTimeout.FiveMinutes -> R.string.settings_lock_5_min
    LockTimeout.FifteenMinutes -> R.string.settings_lock_15_min
}
