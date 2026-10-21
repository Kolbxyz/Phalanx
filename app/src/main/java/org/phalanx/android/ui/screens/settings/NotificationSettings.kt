package org.phalanx.android.ui.screens.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.phalanx.android.R
import org.phalanx.android.data.model.PushFailure
import org.phalanx.android.data.model.PushStatus
import org.phalanx.android.ui.components.SegmentedRow
import org.phalanx.android.ui.components.SegmentedSection
import org.phalanx.android.ui.components.segmentedShape
import org.phalanx.android.ui.theme.Symbols

/** Whether pushes work, which distributor delivers them, and the gateway that relays them. */
@Composable
internal fun NotificationSettings(viewModel: NotificationsViewModel = hiltViewModel()) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val distributors by viewModel.distributors.collectAsStateWithLifecycle()
    val gateway by viewModel.customGateway.collectAsStateWithLifecycle()
    val editing by viewModel.editing.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var permitted by remember { mutableStateOf(notificationsPermitted(context)) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permitted = it
    }
    // Back from the system settings or a distributor's install page: both may have changed.
    LifecycleResumeEffect(Unit) {
        permitted = notificationsPermitted(context)
        viewModel.refreshDistributors()
        onPauseOrDispose {}
    }

    StatusCard(
        status = status,
        permitted = permitted,
        onAllow = { askPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
    )
    if (distributors.isNotEmpty()) {
        val selected = (status as? PushStatus.Active)?.distributor?.packageName
        SegmentedSection(stringResource(R.string.push_distributor_section)) {
            distributors.forEachIndexed { index, distributor ->
                val isSelected = distributor.packageName == selected
                SegmentedRow(
                    headline = distributor.label,
                    shape = segmentedShape(index, distributors.size),
                    leading = { RadioButton(selected = isSelected, onClick = null) },
                    interaction = Modifier.selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = { viewModel.useDistributor(distributor.packageName) }
                    )
                )
            }
        }
    }
    SegmentedSection(stringResource(R.string.push_gateway_section)) {
        SegmentedRow(
            headline = stringResource(R.string.push_gateway),
            supporting = gateway ?: stringResource(R.string.push_gateway_automatic),
            shape = segmentedShape(0, 1),
            leading = { Icon(Symbols.Hub, contentDescription = null) },
            interaction = Modifier.clickable(onClick = viewModel::editGateway)
        )
    }
    if (editing != GatewayEditing.Closed) {
        GatewayDialog(
            current = gateway.orEmpty(),
            invalid = editing == GatewayEditing.Invalid,
            onSave = viewModel::saveGateway,
            onDismiss = viewModel::dismissGateway
        )
    }
}

@Composable
private fun StatusCard(status: PushStatus, permitted: Boolean, onAllow: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val working = status is PushStatus.Active && permitted
    val broken = !permitted || status is PushStatus.Failed || status == PushStatus.NoDistributor
    val (title, body) = statusText(status)
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = when {
            working -> colors.tertiaryContainer
            broken -> colors.errorContainer
            else -> colors.surfaceContainerHigh
        },
        contentColor = when {
            working -> colors.onTertiaryContainer
            broken -> colors.onErrorContainer
            else -> colors.onSurface
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when {
                        working -> Symbols.NotificationsActive
                        broken -> Symbols.NotificationsOff
                        else -> Symbols.NotificationsPaused
                    },
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
                Text(text = title, style = MaterialTheme.typography.titleLarge)
            }
            if (body != null) Text(text = body, style = MaterialTheme.typography.bodyMedium)
            if (!permitted) {
                Button(onClick = onAllow, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text(stringResource(R.string.push_permission))
                }
            }
        }
    }
}

@Composable
private fun statusText(status: PushStatus): Pair<String, String?> = when (status) {
    is PushStatus.Active ->
        stringResource(R.string.push_on) to stringResource(R.string.push_on_summary, status.distributor.label)
    PushStatus.NoDistributor ->
        stringResource(R.string.push_no_distributor) to stringResource(R.string.push_no_distributor_summary)
    PushStatus.ChooseDistributor ->
        stringResource(R.string.push_choose) to stringResource(R.string.push_choose_summary)
    PushStatus.Off, PushStatus.Registering -> stringResource(R.string.push_setting_up) to null
    is PushStatus.Failed -> stringResource(R.string.push_failed) to stringResource(
        when (status.reason) {
            PushFailure.Network -> R.string.push_failed_network
            PushFailure.DistributorActionRequired -> R.string.push_failed_action
            PushFailure.Homeserver -> R.string.push_failed_homeserver
            PushFailure.Distributor -> R.string.push_failed_distributor
        }
    )
}

private fun notificationsPermitted(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
