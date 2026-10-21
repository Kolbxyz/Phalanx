package org.phalanx.android.ui.screens.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.phalanx.android.R
import org.phalanx.android.data.repository.RecoveryStatus
import org.phalanx.android.data.repository.SecurityStatus
import org.phalanx.android.data.repository.VerificationStep
import org.phalanx.android.ui.components.SegmentedRow
import org.phalanx.android.ui.components.SegmentedSection
import org.phalanx.android.ui.components.segmentedShape
import org.phalanx.android.ui.theme.Symbols

/** This session's verification, the account's recovery key, and the list of signed-in devices. */
@Composable
internal fun SecuritySettings(onOpenDevices: () -> Unit, viewModel: SecurityViewModel = hiltViewModel()) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val step by viewModel.verification.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    StatusCard(status, onVerify = viewModel::startVerification, onEnterKey = viewModel::startEntering)
    SegmentedSection(stringResource(R.string.security_recovery)) {
        when (status?.recovery) {
            RecoveryStatus.Off -> ActionRow(
                Symbols.Key, R.string.security_set_up, R.string.security_set_up_summary, ui.busy,
                viewModel::setUpRecovery
            )
            RecoveryStatus.On -> ActionRow(
                Symbols.Password, R.string.security_change_key, R.string.security_change_key_summary, ui.busy,
                viewModel::changeRecoveryKey
            )
            RecoveryStatus.Incomplete -> ActionRow(
                Symbols.Key, R.string.security_enter_key, R.string.security_enter_key_summary, ui.busy,
                viewModel::startEntering
            )
            RecoveryStatus.Unknown, null -> SegmentedRow(
                headline = stringResource(R.string.loading),
                shape = segmentedShape(0, 1),
                leading = { LoadingIndicator(Modifier.size(24.dp)) }
            )
        }
    }
    SegmentedSection(stringResource(R.string.security_devices)) {
        SegmentedRow(
            headline = stringResource(R.string.account_devices),
            supporting = stringResource(R.string.account_devices_summary),
            shape = segmentedShape(0, 1),
            leading = { Icon(Symbols.Devices, contentDescription = null) },
            interaction = Modifier.clickable(onClick = onOpenDevices)
        )
    }

    ui.newKey?.let { RecoveryKeyDialog(it, viewModel::dismissKey) }
    if (ui.entering) EnterKeyDialog(ui.busy, ui.keyRejected, viewModel::enterKey, viewModel::dismissEntering)
    if (ui.failed) FailedDialog(viewModel::dismissFailure)
    if (step != VerificationStep.Idle) {
        VerificationDialog(
            step = step,
            onMatch = viewModel::confirmVerification,
            onNoMatch = viewModel::cancelVerification,
            onClose = viewModel::closeVerification
        )
    }
}

/** Green-ish and calm when verified; otherwise says why it matters and offers both ways to fix it. */
@Composable
private fun StatusCard(status: SecurityStatus?, onVerify: () -> Unit, onEnterKey: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val verified = status?.verified == true
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = if (verified) colors.tertiaryContainer else colors.errorContainer,
        contentColor = if (verified) colors.onTertiaryContainer else colors.onErrorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (verified) Symbols.VerifiedUser else Symbols.GppMaybe,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = stringResource(if (verified) R.string.security_verified else R.string.security_unverified),
                    style = MaterialTheme.typography.titleLarge
                )
            }
            Text(
                text = stringResource(
                    if (verified) R.string.security_verified_summary else R.string.security_unverified_summary
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            if (status != null && !verified) {
                // Stacked: side by side, the second label wraps over several lines on a phone.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Button(onClick = onVerify, modifier = Modifier.fillMaxWidth()) {
                        Icon(Symbols.PhonelinkLock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.security_verify_device), modifier = Modifier.padding(start = 8.dp))
                    }
                    OutlinedButton(onClick = onEnterKey, modifier = Modifier.fillMaxWidth()) {
                        Icon(Symbols.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.security_use_key), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    @StringRes title: Int,
    @StringRes summary: Int,
    busy: Boolean,
    onClick: () -> Unit
) {
    SegmentedRow(
        headline = stringResource(title),
        supporting = stringResource(summary),
        shape = segmentedShape(0, 1),
        leading = { Icon(icon, contentDescription = null) },
        trailing = if (busy) {
            { LoadingIndicator(Modifier.size(24.dp)) }
        } else {
            null
        },
        interaction = Modifier.clickable(enabled = !busy, onClick = onClick)
    )
}
