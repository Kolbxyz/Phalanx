package org.phalanx.android.ui.screens.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.data.model.Person
import org.phalanx.android.data.model.RoomSummary
import org.phalanx.android.ui.components.MatrixAvatar
import org.phalanx.android.ui.screens.roomlist.RoomSource
import org.phalanx.android.ui.screens.roomlist.SourceIcon
import org.phalanx.android.ui.screens.roomlist.formatActivity
import org.phalanx.android.ui.screens.roomlist.label
import org.phalanx.android.ui.theme.Symbols

internal fun sourceOf(chat: RoomSummary): RoomSource = chat.bridge?.let(RoomSource::Network) ?: RoomSource.Matrix

/** A person as one card: who they are, where they were last heard, and a button for each network. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PersonCard(person: Person, onOpenRoom: (String) -> Unit, onUnlink: (Person) -> Unit) {
    val latest = person.chats.maxBy { it.lastActivityMillis ?: 0L }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MatrixAvatar(
                    name = person.name,
                    media = person.chats.firstNotNullOfOrNull { it.avatar },
                    size = 72.dp,
                    shape = MaterialShapes.Cookie9Sided.toShape(),
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    content = MaterialTheme.colorScheme.onTertiaryContainer,
                    textStyle = MaterialTheme.typography.headlineMedium
                )
                Column(Modifier.weight(1f)) {
                    Text(person.name, style = MaterialTheme.typography.titleLarge)
                    latest.lastActivityMillis?.let { millis ->
                        Text(
                            text = stringResource(
                                R.string.people_latest,
                                stringResource(sourceOf(latest).label),
                                formatActivity(millis)
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = { onUnlink(person) }) {
                    Icon(Symbols.Close, contentDescription = stringResource(R.string.people_unlink))
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                person.chats.forEach { chat -> NetworkButton(chat) { onOpenRoom(chat.id) } }
            }
        }
    }
}

@Composable
private fun NetworkButton(chat: RoomSummary, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick) {
        BadgedBox(badge = { if (chat.unreadCount > 0) Badge() }) {
            SourceIcon(sourceOf(chat), size = 20.dp)
        }
        Text(stringResource(sourceOf(chat).label), Modifier.padding(start = 8.dp))
    }
}

/** Two chats that may be one person: their avatars overlap, a mark between them says so. */
@Composable
internal fun OverlappingAvatars(first: RoomSummary, second: RoomSummary) {
    Box(Modifier.size(width = 88.dp, height = 56.dp)) {
        listOf(first to 0.dp, second to 32.dp).forEach { (chat, offset) ->
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.offset(x = offset)
            ) {
                MatrixAvatar(
                    name = chat.name,
                    media = chat.avatar,
                    size = 56.dp,
                    modifier = Modifier.padding(3.dp),
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                    textStyle = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}
