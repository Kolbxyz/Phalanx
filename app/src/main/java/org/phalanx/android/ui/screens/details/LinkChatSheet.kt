package org.phalanx.android.ui.screens.details

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.core.contacts.nameKey
import org.phalanx.android.data.model.RoomSummary
import org.phalanx.android.ui.components.MatrixAvatar
import org.phalanx.android.ui.components.ModalSheet
import org.phalanx.android.ui.screens.roomlist.RoomSource
import org.phalanx.android.ui.screens.roomlist.SourceIcon
import org.phalanx.android.ui.screens.roomlist.label
import org.phalanx.android.ui.theme.Symbols

/** The user's other chats, filtered by name, to pick the one that is the same conversation. */
@Composable
internal fun LinkChatSheet(candidates: List<RoomSummary>, onPick: (RoomSummary) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    ModalSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.details_link_chat_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            leadingIcon = { Icon(Symbols.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.details_link_chat_search)) },
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
        val key = nameKey(query)
        val shown = candidates.filter { key.isEmpty() || nameKey(it.name).contains(key) }
        if (shown.isEmpty()) {
            Text(
                text = stringResource(R.string.details_link_chat_none),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(24.dp)
            )
        }
        LazyColumn {
            items(shown, key = { it.id }) { chat ->
                val source = chat.bridge?.let(RoomSource::Network) ?: RoomSource.Matrix
                ListItem(
                    headlineContent = { Text(chat.name) },
                    supportingContent = { Text(stringResource(source.label)) },
                    leadingContent = { MatrixAvatar(name = chat.name, media = chat.avatar, size = 40.dp) },
                    trailingContent = { SourceIcon(source, size = 20.dp) },
                    modifier = Modifier.clickable { onPick(chat) }
                )
            }
        }
    }
}
