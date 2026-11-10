package org.phalanx.android.ui.screens.details

import androidx.compose.foundation.layout.padding
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

/**
 * "Also on": the same conversation's chats on other networks, each opening that chat, and a chip to
 * link another of the user's chats to it by hand, one-to-one or group.
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
    AlsoOnChips(
        chats = state.others,
        onOpen = onOpenRoom,
        onLink = { picking = true },
        modifier = Modifier.padding(horizontal = 8.dp)
    )
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
