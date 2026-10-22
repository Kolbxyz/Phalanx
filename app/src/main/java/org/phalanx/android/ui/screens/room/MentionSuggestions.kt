package org.phalanx.android.ui.screens.room

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.ui.components.MatrixAvatar
import org.phalanx.android.ui.theme.Symbols

private val AvatarSize = 32.dp

/** Above the field while "@..." is being typed: members, "@room" and "@silent" to complete it with. */
@Composable
internal fun MentionSuggestions(suggestions: List<MentionSuggestion>, onPick: (MentionSuggestion) -> Unit) {
    Column {
        suggestions.forEach { suggestion ->
            ListItem(
                headlineContent = { Text(headline(suggestion), maxLines = 1) },
                supportingContent = { Text(supporting(suggestion), maxLines = 1) },
                leadingContent = { Leading(suggestion) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable(role = Role.Button) { onPick(suggestion) }
            )
        }
    }
}

@Composable
private fun Leading(suggestion: MentionSuggestion) = when (suggestion) {
    is MentionSuggestion.User -> MatrixAvatar(suggestion.member.name, suggestion.member.avatar, AvatarSize)
    MentionSuggestion.Room -> SymbolBox { Icon(Symbols.NotificationsActive, contentDescription = null) }
    MentionSuggestion.Silent -> SymbolBox { Icon(Symbols.NotificationsOff, contentDescription = null) }
}

@Composable
private fun SymbolBox(content: @Composable () -> Unit) =
    Box(Modifier.size(AvatarSize), contentAlignment = Alignment.Center) { content() }

@Composable
private fun headline(suggestion: MentionSuggestion): String = when (suggestion) {
    is MentionSuggestion.User -> suggestion.member.name
    MentionSuggestion.Room -> stringResource(R.string.composer_mention_room_token)
    MentionSuggestion.Silent -> stringResource(R.string.composer_mention_silent_token)
}

@Composable
private fun supporting(suggestion: MentionSuggestion): String = when (suggestion) {
    is MentionSuggestion.User -> suggestion.member.userId
    MentionSuggestion.Room -> stringResource(R.string.composer_mention_room)
    MentionSuggestion.Silent -> stringResource(R.string.composer_mention_silent)
}
