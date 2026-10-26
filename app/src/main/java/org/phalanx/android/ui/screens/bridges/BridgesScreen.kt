package org.phalanx.android.ui.screens.bridges

import androidx.annotation.StringRes
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.phalanx.android.R
import org.phalanx.android.data.model.BridgeNetwork
import org.phalanx.android.data.model.BridgeStatus
import org.phalanx.android.ui.components.BackButton
import org.phalanx.android.ui.components.SegmentGap
import org.phalanx.android.ui.components.SegmentedRow
import org.phalanx.android.ui.components.pressShrink
import org.phalanx.android.ui.components.segmentedShape
import org.phalanx.android.ui.theme.Symbols

/**
 * Connect other chat networks, one row each: the bridges the homeserver runs can be connected,
 * which opens the chat with the bridge's bot where it walks through signing in.
 */
@Composable
fun BridgesScreen(onBack: () -> Unit, onOpenRoom: (String) -> Unit, viewModel: BridgesViewModel = hiltViewModel()) {
    val statuses by viewModel.statuses.collectAsStateWithLifecycle()
    val connecting by viewModel.connecting.collectAsStateWithLifecycle()
    val failed by viewModel.failed.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.open.collect(onOpenRoom) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.bridges_title)) },
                navigationIcon = { BackButton(onBack) },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        val list = statuses
        if (list == null) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { LoadingIndicator() }
            return@Scaffold
        }
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
            item {
                Text(
                    stringResource(if (failed) R.string.bridges_connect_failed else R.string.bridges_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 16.dp)
                )
            }
            itemsIndexed(list, key = { _, status -> status.network.name }) { index, status ->
                BridgeRow(status, segmentedShape(index, list.size), connecting, viewModel::connect, onOpenRoom)
            }
        }
    }
}

@Composable
private fun BridgeRow(
    status: BridgeStatus,
    shape: Shape,
    connecting: BridgeNetwork?,
    onConnect: (BridgeStatus.Available) -> Unit,
    onOpen: (String) -> Unit
) {
    val roomId = (status as? BridgeStatus.Available)?.roomId
    SegmentedRow(
        headline = stringResource(titleOf(status.network)),
        supporting = stringResource(
            when {
                status is BridgeStatus.Unavailable -> R.string.bridges_unavailable
                roomId != null -> R.string.bridges_chatting
                else -> R.string.bridges_available
            }
        ),
        shape = shape,
        leading = { Icon(Symbols.Chat, contentDescription = null) },
        trailing = when {
            status !is BridgeStatus.Available -> null
            connecting == status.network -> { { LoadingIndicator(Modifier.size(32.dp)) } }
            else -> {
                {
                    val interaction = remember { MutableInteractionSource() }
                    if (roomId != null) {
                        FilledTonalButton(
                            onClick = { onOpen(roomId) },
                            modifier = Modifier.pressShrink(interaction),
                            interactionSource = interaction
                        ) { Text(stringResource(R.string.bridges_open)) }
                    } else {
                        Button(
                            onClick = { onConnect(status) },
                            enabled = connecting == null,
                            modifier = Modifier.pressShrink(interaction),
                            interactionSource = interaction
                        ) { Text(stringResource(R.string.bridges_connect)) }
                    }
                }
            }
        }
    )
}

@StringRes
private fun titleOf(network: BridgeNetwork): Int = when (network) {
    BridgeNetwork.WhatsApp -> R.string.bridge_whatsapp
    BridgeNetwork.Discord -> R.string.bridge_discord
    BridgeNetwork.Instagram -> R.string.bridge_instagram
    BridgeNetwork.Messenger -> R.string.bridge_messenger
    BridgeNetwork.Telegram -> R.string.bridge_telegram
    BridgeNetwork.Signal -> R.string.bridge_signal
}
