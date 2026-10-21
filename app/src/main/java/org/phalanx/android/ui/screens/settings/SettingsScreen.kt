package org.phalanx.android.ui.screens.settings

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.phalanx.android.R
import org.phalanx.android.data.model.ThemeMode
import org.phalanx.android.ui.components.BackButton
import org.phalanx.android.ui.components.ConnectedToggleGroup
import org.phalanx.android.ui.components.SegmentedRow
import org.phalanx.android.ui.components.SegmentedSection
import org.phalanx.android.ui.components.segmentedShape

@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = { BackButton(onBack) },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        val current = settings ?: return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            SegmentedSection(stringResource(R.string.settings_appearance)) {
                ConnectedToggleGroup(
                    options = ThemeMode.entries,
                    selected = current.themeMode,
                    label = { stringResource(it.labelRes()) },
                    onSelect = viewModel::setThemeMode,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                SwitchGroup(
                    buildList {
                        // Wallpaper colours exist from Android 12; below that the switch would do nothing.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            add(SwitchSetting(R.string.settings_dynamic_color, R.string.settings_dynamic_color_summary, current.dynamicColor, viewModel::setDynamicColor))
                        }
                        add(SwitchSetting(R.string.settings_amoled, R.string.settings_amoled_summary, current.amoledBlack, viewModel::setAmoledBlack))
                        add(SwitchSetting(R.string.settings_reduce_motion, R.string.settings_reduce_motion_summary, current.reduceMotion, viewModel::setReduceMotion))
                    }
                )
            }
            SegmentedSection(stringResource(R.string.settings_privacy)) {
                SwitchGroup(
                    listOf(
                        SwitchSetting(R.string.settings_read_receipts, R.string.settings_read_receipts_summary, current.publicReadReceipts, viewModel::setPublicReadReceipts)
                    )
                )
            }
        }
    }
}

private class SwitchSetting(
    @StringRes val title: Int,
    @StringRes val summary: Int,
    val checked: Boolean,
    val onChange: (Boolean) -> Unit
)

@Composable
private fun SwitchGroup(items: List<SwitchSetting>) {
    items.forEachIndexed { index, item ->
        SegmentedRow(
            headline = stringResource(item.title),
            supporting = stringResource(item.summary),
            shape = segmentedShape(index, items.size),
            // The whole row toggles; the switch itself only shows the state, so TalkBack reads one control.
            interaction = Modifier.toggleable(value = item.checked, role = Role.Switch, onValueChange = item.onChange),
            trailing = { Switch(checked = item.checked, onCheckedChange = null) }
        )
    }
}

@StringRes
private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.System -> R.string.settings_theme_system
    ThemeMode.Light -> R.string.settings_theme_light
    ThemeMode.Dark -> R.string.settings_theme_dark
}
