package org.phalanx.android.ui.screens.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.phalanx.android.R
import org.phalanx.android.data.model.Device
import org.phalanx.android.ui.components.BackButton
import org.phalanx.android.ui.components.SegmentGap
import org.phalanx.android.ui.components.SegmentedRow
import org.phalanx.android.ui.components.segmentedShape
import org.phalanx.android.ui.theme.Symbols
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun DevicesScreen(onBack: () -> Unit, viewModel: DevicesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.account_devices)) },
                navigationIcon = { BackButton(onBack) },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        val devices = state.devices
        when {
            devices != null -> DeviceList(devices, padding, viewModel::askRemove)
            state.loadFailed -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(R.string.devices_load_failed), color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = viewModel::load) { Text(stringResource(R.string.devices_retry)) }
            }
            else -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { LoadingIndicator() }
        }
    }
    state.removing?.let { device ->
        RemoveDialog(device, state, viewModel::onPassword, viewModel::remove, viewModel::dismissRemove)
    }
}

@Composable
private fun DeviceList(devices: List<Device>, padding: PaddingValues, onRemove: (Device) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding() + 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(SegmentGap)
    ) {
        itemsIndexed(devices, key = { _, device -> device.id }) { index, device ->
            SegmentedRow(
                headline = device.name?.takeIf { it.isNotBlank() } ?: device.id,
                supporting = deviceSummary(device),
                shape = segmentedShape(index, devices.size),
                // This device is signed out from the Account screen, which also cleans up locally.
                trailing = if (device.isCurrent) {
                    null
                } else {
                    {
                        IconButton(onClick = { onRemove(device) }) {
                            Icon(Symbols.Delete, contentDescription = stringResource(R.string.devices_remove))
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun deviceSummary(device: Device): String {
    if (device.isCurrent) return stringResource(R.string.devices_this_device, device.id)
    val seen = device.lastSeenMillis?.let {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))
    }
    return listOfNotNull(device.id, seen, device.lastSeenIp).joinToString(" · ")
}

@Composable
private fun RemoveDialog(
    device: Device,
    state: DevicesUiState,
    onPassword: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.devices_remove_title, device.name ?: device.id)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.devices_remove_body))
                OutlinedTextField(
                    value = state.password,
                    onValueChange = onPassword,
                    label = { Text(stringResource(R.string.login_password)) },
                    singleLine = true,
                    enabled = !state.busy,
                    isError = state.wrongPassword || state.removeFailed,
                    supportingText = when {
                        state.wrongPassword -> {
                            { Text(stringResource(R.string.account_wrong_password)) }
                        }
                        state.removeFailed -> {
                            { Text(stringResource(R.string.devices_remove_failed)) }
                        }
                        else -> null
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = state.password.isNotEmpty() && !state.busy,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                if (state.busy) LoadingIndicator(Modifier.size(20.dp)) else Text(stringResource(R.string.devices_remove))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.busy) { Text(stringResource(R.string.account_cancel)) }
        }
    )
}
