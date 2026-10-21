package org.phalanx.android.ui.screens.settings

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.phalanx.android.BuildConfig
import org.phalanx.android.R
import org.phalanx.android.data.model.AppSettings
import org.phalanx.android.data.model.TextSize
import org.phalanx.android.data.model.ThemeMode
import org.phalanx.android.ui.components.BackButton
import org.phalanx.android.ui.components.ConnectedToggleGroup
import org.phalanx.android.ui.components.SegmentedRow
import org.phalanx.android.ui.components.SegmentedSection
import org.phalanx.android.ui.components.segmentedShape

@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val blocked by viewModel.blocked.collectAsStateWithLifecycle()
    val unblockFailed by viewModel.unblockFailed.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val failure = stringResource(R.string.settings_unblock_failed)
    LaunchedEffect(unblockFailed) {
        if (unblockFailed) {
            snackbar.showSnackbar(failure)
            viewModel.dismissFailure()
        }
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = { BackButton(onBack) },
                scrollBehavior = scrollBehavior
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        val current = settings ?: return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            AppearanceSection(current, viewModel)
            ConversationsSection(current, viewModel)
            MediaSection(current, viewModel)
            PrivacySection(current, viewModel)
            BlockedUsersSection(blocked, viewModel::unblock)
            AboutSection()
        }
    }
}

@Composable
private fun MediaSection(current: AppSettings, viewModel: SettingsViewModel) {
    SegmentedSection(stringResource(R.string.settings_media)) {
        SwitchGroup(
            listOf(
                SwitchSetting(Icons.Rounded.Image, R.string.settings_auto_load_media,
                    R.string.settings_auto_load_media_summary, current.autoLoadMedia, viewModel::setAutoLoadMedia),
                SwitchSetting(Icons.Rounded.HighQuality, R.string.settings_full_quality,
                    R.string.settings_full_quality_summary, current.fullQualityImages, viewModel::setFullQualityImages)
            )
        )
    }
}

@Composable
private fun PrivacySection(current: AppSettings, viewModel: SettingsViewModel) {
    SegmentedSection(stringResource(R.string.settings_privacy)) {
        SwitchGroup(
            listOf(
                SwitchSetting(Icons.Rounded.DoneAll, R.string.settings_read_receipts,
                    R.string.settings_read_receipts_summary, current.publicReadReceipts,
                    viewModel::setPublicReadReceipts),
                SwitchSetting(Icons.Rounded.Keyboard, R.string.settings_send_typing,
                    R.string.settings_send_typing_summary, current.sendTypingNotices,
                    viewModel::setSendTypingNotices)
            )
        )
    }
}

@Composable
private fun AboutSection() {
    SegmentedSection(stringResource(R.string.settings_about)) {
        SegmentedRow(
            headline = stringResource(R.string.app_name),
            supporting = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
            shape = segmentedShape(0, 1),
            leading = { Icon(Icons.Rounded.Info, contentDescription = null) }
        )
    }
}

@Composable
private fun AppearanceSection(current: AppSettings, viewModel: SettingsViewModel) {
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
                    add(SwitchSetting(Icons.Rounded.Palette, R.string.settings_dynamic_color,
                        R.string.settings_dynamic_color_summary, current.dynamicColor, viewModel::setDynamicColor))
                }
                add(SwitchSetting(Icons.Rounded.Contrast, R.string.settings_amoled,
                    R.string.settings_amoled_summary, current.amoledBlack, viewModel::setAmoledBlack))
                add(SwitchSetting(Icons.Rounded.Animation, R.string.settings_reduce_motion,
                    R.string.settings_reduce_motion_summary, current.reduceMotion, viewModel::setReduceMotion))
            }
        )
    }
}

@Composable
private fun ConversationsSection(current: AppSettings, viewModel: SettingsViewModel) {
    SegmentedSection(stringResource(R.string.settings_conversations)) {
        Text(
            text = stringResource(R.string.settings_text_size),
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )
        ConnectedToggleGroup(
            options = TextSize.entries,
            selected = current.messageTextSize,
            label = { stringResource(it.labelRes()) },
            onSelect = viewModel::setMessageTextSize,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        SwitchGroup(
            listOf(
                SwitchSetting(Icons.AutoMirrored.Rounded.KeyboardReturn, R.string.settings_enter_to_send,
                    R.string.settings_enter_to_send_summary, current.enterToSend, viewModel::setEnterToSend),
                SwitchSetting(Icons.Rounded.FormatBold, R.string.settings_markdown,
                    R.string.settings_markdown_summary, current.markdown, viewModel::setMarkdown)
            )
        )
    }
}

@StringRes
private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.System -> R.string.settings_theme_system
    ThemeMode.Light -> R.string.settings_theme_light
    ThemeMode.Dark -> R.string.settings_theme_dark
}

@StringRes
private fun TextSize.labelRes(): Int = when (this) {
    TextSize.Small -> R.string.settings_text_small
    TextSize.Default -> R.string.settings_text_default
    TextSize.Large -> R.string.settings_text_large
}
