package org.phalanx.android.ui.screens.account

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.phalanx.android.R
import org.phalanx.android.data.repository.AccountPage
import org.phalanx.android.ui.components.BackButton
import org.phalanx.android.ui.components.ScrollHairline
import org.phalanx.android.ui.components.SegmentedRow
import org.phalanx.android.ui.components.SegmentedSection
import org.phalanx.android.ui.components.scrolledPastTop
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
    val scroll = rememberScrollState()
    OpenBrowser(state.browserUrl, viewModel::onBrowserOpened)
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.account_title)) },
                    navigationIcon = { BackButton(onBack) }
                )
                ScrollHairline(scroll.scrolledPastTop())
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(padding)
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            profile?.let {
                ProfileHeader(
                    profile = it,
                    avatar = avatar,
                    state = state,
                    onPickPhoto = {
                        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onRemovePhoto = viewModel::removeAvatar,
                    onEditName = viewModel::editName,
                    onNameChange = viewModel::onNameChange,
                    onSaveName = viewModel::saveName,
                    onCancelName = viewModel::dismissName
                )
                HowOthersSeeYou(it, avatar, state.nameDraft)
            }
            state.details?.let { details ->
                SegmentedSection(stringResource(R.string.account_section_device)) {
                    // Provider-managed accounts list devices on the provider's site, below.
                    val count = if (details.managedByProvider) 2 else 3
                    CopyRow(
                        stringResource(R.string.account_server),
                        details.homeserver,
                        Symbols.Dns,
                        index = 0,
                        count = count
                    )
                    CopyRow(
                        stringResource(R.string.account_device_id),
                        details.deviceId,
                        Symbols.Key,
                        index = 1,
                        count = count
                    )
                    if (!details.managedByProvider) {
                        SegmentedRow(
                            headline = stringResource(R.string.account_devices),
                            supporting = stringResource(R.string.account_devices_summary),
                            shape = segmentedShape(2, count),
                            leading = { IconTile(Symbols.Devices) },
                            trailing = { Icon(Symbols.KeyboardArrowRight, contentDescription = null) },
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
            val colors = MaterialTheme.colorScheme
            SegmentedSection(stringResource(R.string.account_section_app)) {
                SegmentedRow(
                    headline = stringResource(R.string.account_sign_out),
                    shape = segmentedShape(0, 1),
                    headlineColor = colors.error,
                    leading = { IconTile(Symbols.ExitToApp, colors.errorContainer, colors.onErrorContainer) },
                    interaction = Modifier.clickable(enabled = !state.signingOut, onClick = viewModel::askSignOut)
                )
            }
            DangerZone(viewModel::startDeactivation)
        }
    }
    state.deactivation?.let { draft ->
        DeactivateSheet(
            draft = draft,
            onPassword = viewModel::onDeactivationPassword,
            onErase = viewModel::onDeactivationErase,
            onConfirm = viewModel::deactivate,
            onDismiss = viewModel::dismissDeactivation
        )
    }
    if (state.confirmingSignOut) {
        SignOutSheet(state.signingOut, viewModel::signOut, viewModel::dismissSignOut)
    }
}

/** Closing the account, apart in the error colour, so nothing everyday sits next to it. */
@Composable
private fun DangerZone(onDeactivate: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = stringResource(R.string.account_section_danger),
        style = MaterialTheme.typography.labelLarge,
        color = colors.error,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp).semantics { heading() }
    )
    Surface(
        onClick = onDeactivate,
        shape = RoundedCornerShape(24.dp),
        color = colors.errorContainer,
        contentColor = colors.onErrorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(stringResource(R.string.account_deactivate), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.account_deactivate_summary), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
