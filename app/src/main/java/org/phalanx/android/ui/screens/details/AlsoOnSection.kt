package org.phalanx.android.ui.screens.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.phalanx.android.R
import org.phalanx.android.ui.screens.roomlist.RoomSource
import org.phalanx.android.ui.screens.roomlist.SourceIcon
import org.phalanx.android.ui.screens.roomlist.label

/** "Also on": the same person's chats on other networks, each opening that chat. Nothing when none is linked. */
@Composable
internal fun AlsoOnSection(onOpenRoom: (String) -> Unit, viewModel: AlsoOnViewModel = hiltViewModel()) {
    val others by viewModel.others.collectAsStateWithLifecycle()
    if (others.isEmpty()) return
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
        others.forEach { chat ->
            val source = chat.bridge?.let(RoomSource::Network) ?: RoomSource.Matrix
            AssistChip(
                onClick = { onOpenRoom(chat.id) },
                label = { Text(stringResource(source.label)) },
                leadingIcon = { SourceIcon(source, size = 18.dp) }
            )
        }
    }
}
