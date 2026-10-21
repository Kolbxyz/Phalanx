package org.phalanx.android.ui.screens.room

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.phalanx.android.R

/**
 * A small bubble at the bottom of the conversation while others type: the Expressive morphing
 * indicator, then who it is. Names only in group chats; in a DM it can only be the other person.
 */
@Composable
internal fun TypingIndicator(names: List<String>, isGroupChat: Boolean, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.padding(top = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LoadingIndicator(Modifier.size(24.dp))
            Text(
                text = if (isGroupChat) typingLabel(names) else stringResource(R.string.typing_dm),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun typingLabel(names: List<String>): String = when (names.size) {
    1 -> stringResource(R.string.typing_one, names[0])
    2 -> stringResource(R.string.typing_two, names[0], names[1])
    else -> pluralStringResource(R.plurals.typing_many, names.size - 1, names[0], names.size - 1)
}
