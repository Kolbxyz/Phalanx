package org.phalanx.android.ui.screens.account

import android.content.ActivityNotFoundException
import android.content.ClipData
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.phalanx.android.R
import org.phalanx.android.data.repository.AccountPage
import org.phalanx.android.data.repository.AccountProfile
import org.phalanx.android.ui.components.BackButton
import org.phalanx.android.ui.components.InitialAvatar
import org.phalanx.android.ui.components.SegmentedRow
import org.phalanx.android.ui.components.SegmentedSection
import org.phalanx.android.ui.components.segmentedShape

@Composable
fun AccountScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDevices: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val avatar by viewModel.avatar.collectAsStateWithLifecycle()
    // The system photo picker: no storage permission, and the app only ever sees the one image chosen.
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.setAvatar(uri)
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    OpenBrowser(state.browserUrl, viewModel::onBrowserOpened)
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.account_title)) },
                navigationIcon = { BackButton(onBack) },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            profile?.let { ProfileHeader(it, avatar) }
            SegmentedSection(stringResource(R.string.account_section_profile)) {
                val hasAvatar = profile?.avatarUrl != null
                val count = if (hasAvatar) 3 else 2
                SegmentedRow(
                    headline = stringResource(R.string.account_display_name),
                    supporting = profile?.takeIf { it.hasDisplayName }?.displayName
                        ?: stringResource(R.string.account_display_name_unset),
                    shape = segmentedShape(0, count),
                    interaction = Modifier.clickable(enabled = profile != null, onClick = viewModel::editName)
                )
                SegmentedRow(
                    headline = stringResource(R.string.account_photo_change),
                    supporting = if (state.avatarFailed) stringResource(R.string.account_photo_failed) else null,
                    shape = segmentedShape(1, count),
                    trailing = if (state.avatarBusy) {
                        { LoadingIndicator(Modifier.size(24.dp)) }
                    } else {
                        null
                    },
                    interaction = Modifier.clickable(enabled = !state.avatarBusy) {
                        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                )
                if (hasAvatar) {
                    SegmentedRow(
                        headline = stringResource(R.string.account_photo_remove),
                        shape = segmentedShape(2, count),
                        interaction = Modifier.clickable(enabled = !state.avatarBusy, onClick = viewModel::removeAvatar)
                    )
                }
            }
            state.details?.let { details ->
                SegmentedSection(stringResource(R.string.account_section_device)) {
                    val count = if (details.managedByProvider) 2 else 3
                    CopyRow(stringResource(R.string.account_device_id), details.deviceId, index = 0, count = count)
                    CopyRow(stringResource(R.string.account_server), details.homeserver, index = 1, count = count)
                    // Provider-managed accounts get the device list on the provider's site, below.
                    if (!details.managedByProvider) {
                        SegmentedRow(
                            headline = stringResource(R.string.account_devices),
                            supporting = stringResource(R.string.account_devices_summary),
                            shape = segmentedShape(2, count),
                            interaction = Modifier.clickable(onClick = onOpenDevices)
                        )
                    }
                }
                if (details.managedByProvider) {
                    SegmentedSection(stringResource(R.string.account_section_provider)) {
                        SegmentedRow(
                            headline = stringResource(R.string.account_manage),
                            supporting = stringResource(R.string.account_manage_summary),
                            shape = segmentedShape(0, 2),
                            interaction = Modifier.clickable { viewModel.openManage(AccountPage.Profile) }
                        )
                        SegmentedRow(
                            headline = stringResource(R.string.account_devices),
                            supporting = stringResource(R.string.account_devices_summary),
                            shape = segmentedShape(1, 2),
                            interaction = Modifier.clickable { viewModel.openManage(AccountPage.Devices) }
                        )
                    }
                }
            }
            SegmentedSection(stringResource(R.string.account_section_app)) {
                SegmentedRow(
                    headline = stringResource(R.string.settings_title),
                    shape = segmentedShape(0, 2),
                    leading = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    interaction = Modifier.clickable(onClick = onOpenSettings)
                )
                SegmentedRow(
                    headline = stringResource(R.string.account_sign_out),
                    shape = segmentedShape(1, 2),
                    headlineColor = MaterialTheme.colorScheme.error,
                    leading = {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    },
                    interaction = Modifier.clickable(enabled = !state.signingOut, onClick = viewModel::askSignOut)
                )
            }
            SegmentedSection(stringResource(R.string.account_section_danger)) {
                SegmentedRow(
                    headline = stringResource(R.string.account_deactivate),
                    supporting = stringResource(R.string.account_deactivate_summary),
                    shape = segmentedShape(0, 1),
                    headlineColor = MaterialTheme.colorScheme.error,
                    interaction = Modifier.clickable(onClick = viewModel::startDeactivation)
                )
            }
        }
    }
    state.deactivation?.let { draft ->
        DeactivateDialog(
            draft = draft,
            onPassword = viewModel::onDeactivationPassword,
            onErase = viewModel::onDeactivationErase,
            onConfirm = viewModel::deactivate,
            onDismiss = viewModel::dismissDeactivation
        )
    }
    state.nameDraft?.let { draft ->
        DisplayNameDialog(draft, state.savingName, state.nameFailed, viewModel::onNameChange, viewModel::saveName, viewModel::dismissName)
    }
    if (state.confirmingSignOut) {
        SignOutDialog(state.signingOut, viewModel::signOut, viewModel::dismissSignOut)
    }
}

/** A cookie avatar with the name and Matrix ID under it, centred like a profile card. */
@Composable
private fun ProfileHeader(profile: AccountProfile, avatar: ImageBitmap?) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        InitialAvatar(
            name = profile.label,
            shape = MaterialShapes.Cookie9Sided.toShape(),
            size = 96.dp,
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer,
            image = avatar,
            textStyle = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(profile.label, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        if (profile.hasDisplayName) {
            Text(
                text = profile.userId,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** A value worth pasting into a support thread or a verification prompt; tapping copies it. */
@Composable
private fun CopyRow(label: String, value: String, index: Int, count: Int) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    SegmentedRow(
        headline = label,
        supporting = value,
        shape = segmentedShape(index, count),
        interaction = Modifier.clickable(onClickLabel = stringResource(R.string.account_copy)) {
            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, value))) }
        }
    )
}

@Composable
private fun OpenBrowser(url: String?, onOpened: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(url) {
        if (url == null) return@LaunchedEffect
        try {
            CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, url.toUri())
        } catch (_: ActivityNotFoundException) {
            // No browser at all: nothing else could show the page either.
        }
        onOpened()
    }
}
