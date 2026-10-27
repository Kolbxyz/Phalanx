package org.phalanx.android.ui.screens.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.phalanx.android.R
import org.phalanx.android.ui.screens.roomlist.RoomSource
import org.phalanx.android.ui.screens.roomlist.SourceIcon
import org.phalanx.android.ui.screens.roomlist.label
import org.phalanx.android.ui.theme.Symbols

/**
 * "Also on": the same person's chats on other networks, each opening that chat, and a chip to link
 * another of the user's one-to-one chats to them by hand.
 */
@Composable
internal fun AlsoOnSection(onOpenRoom: (String) -> Unit, viewModel: AlsoOnViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val failed by viewModel.failed.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf(false) }
    Text(
        text = stringResource(R.string.details_also_on),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 8.dp)
    ) {
        state.others.forEach { chat ->
            val source = chat.bridge?.let(RoomSource::Network) ?: RoomSource.Matrix
            AssistChip(
                onClick = { onOpenRoom(chat.id) },
                label = { Text(stringResource(source.label)) },
                leadingIcon = { SourceIcon(source, size = 18.dp) }
            )
        }
        AssistChip(
            onClick = { picking = true },
            label = { Text(stringResource(R.string.details_link_chat)) },
            leadingIcon = { Icon(Symbols.Link, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
    }
    if (failed) {
        Text(
            text = stringResource(R.string.people_failed),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
    if (picking) {
        LinkChatSheet(
            candidates = state.candidates,
            onPick = { chat ->
                picking = false
                viewModel.link(chat)
            },
            onDismiss = { picking = false }
        )
    }
}
