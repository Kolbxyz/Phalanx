package org.phalanx.android.ui.screens.account

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.phalanx.android.R
import org.phalanx.android.data.repository.AccountPage
import org.phalanx.android.ui.components.BackButton
import org.phalanx.android.ui.components.SegmentedRow
import org.phalanx.android.ui.components.SegmentedSection
import org.phalanx.android.ui.components.segmentedShape
import org.phalanx.android.ui.theme.Symbols

@Composable
fun AccountScreen(
    onBack: () -> Unit,
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
            profile?.let {
                ProfileHeader(it, avatar, busy = state.avatarBusy, failed = state.avatarFailed) {
                    pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            }
            SegmentedSection(stringResource(R.string.account_section_profile)) {
                val hasAvatar = profile?.avatarUrl != null
                val count = if (hasAvatar) 2 else 1
                SegmentedRow(
                    headline = stringResource(R.string.account_display_name),
                    supporting = profile?.takeIf { it.hasDisplayName }?.displayName
                        ?: stringResource(R.string.account_display_name_unset),
                    shape = segmentedShape(0, count),
                    interaction = Modifier.clickable(enabled = profile != null, onClick = viewModel::editName)
                )
                if (hasAvatar) {
                    SegmentedRow(
                        headline = stringResource(R.string.account_photo_remove),
                        shape = segmentedShape(1, count),
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
                    headline = stringResource(R.string.account_sign_out),
                    shape = segmentedShape(0, 1),
                    headlineColor = MaterialTheme.colorScheme.error,
                    leading = {
                        Icon(
                            Symbols.ExitToApp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
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
        DisplayNameDialog(
            draft,
            state.savingName,
            state.nameFailed,
            viewModel::onNameChange,
            viewModel::saveName,
            viewModel::dismissName
        )
    }
    if (state.confirmingSignOut) {
        SignOutDialog(state.signingOut, viewModel::signOut, viewModel::dismissSignOut)
    }
}
