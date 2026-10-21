package org.phalanx.android.ui.screens.room

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.ui.components.MatrixAvatar

/** Everyone who reacted with one emoji, in the order they did. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReactorsSheet(reactors: Reactors, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        val members = reactors.members
        Text(
            text = if (members == null) {
                reactors.key
            } else {
                pluralStringResource(R.plurals.timeline_reactors_title, members.size, reactors.key, members.size)
            },
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        if (members == null) {
            Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { LoadingIndicator() }
        } else {
            LazyColumn(Modifier.navigationBarsPadding()) {
                items(members, key = { it.userId }) { member ->
                    ListItem(
                        headlineContent = { Text(member.name, maxLines = 1) },
                        supportingContent = { Text(member.userId, maxLines = 1) },
                        leadingContent = { MatrixAvatar(member.name, member.avatar, 40.dp) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    )
                }
            }
        }
    }
}
