package org.phalanx.android.ui.screens.room

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.data.model.RoomSummary
import org.phalanx.android.ui.screens.people.sourceOf
import org.phalanx.android.ui.screens.roomlist.SourceIcon
import org.phalanx.android.ui.screens.roomlist.label

/** The person's thread the timeline belongs to; null in any other room. */
internal val LocalPersonThread = compositionLocalOf<PersonThread?> { null }

/** Above the first message of a run from one network: which network it came through. */
@Composable
internal fun NetworkTag(chat: RoomSummary, isOwn: Boolean) {
    val source = sourceOf(chat)
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp, if (isOwn) Alignment.End else Alignment.Start),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        SourceIcon(source, size = 14.dp)
        Text(
            stringResource(source.label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Which network the next message goes through, above the composer of a person's thread. A reply or
 * an edit goes where its message came from, so [mode] fixes it then and the choice is not offered.
 */
@Composable
internal fun NetworkPicker(
    thread: PersonThread,
    mode: ComposerMode,
    roomOf: (entryId: String) -> String,
    onChoose: (String) -> Unit
) {
    val message = (mode as? ComposerMode.Reply)?.message ?: (mode as? ComposerMode.Edit)?.message
    val replyingIn = message?.let { thread.chats.firstOrNull { chat -> chat.id == roomOf(it.id) } }
    val shown = replyingIn ?: thread.target
    var open by remember { mutableStateOf(false) }
    Box(Modifier.padding(start = 12.dp)) {
        AssistChip(
            onClick = { open = true },
            enabled = replyingIn == null,
            label = { Text(stringResource(R.string.composer_network, stringResource(sourceOf(shown).label))) },
            leadingIcon = { SourceIcon(sourceOf(shown), size = 18.dp) }
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            thread.chats.forEach { chat ->
                DropdownMenuItem(
                    text = { Text(stringResource(sourceOf(chat).label)) },
                    leadingIcon = { SourceIcon(sourceOf(chat), size = 20.dp) },
                    onClick = {
                        open = false
                        onChoose(chat.id)
                    }
                )
            }
        }
    }
}
