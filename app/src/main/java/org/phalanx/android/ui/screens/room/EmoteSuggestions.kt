package org.phalanx.android.ui.screens.room

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.phalanx.android.data.model.CustomEmote
import org.phalanx.android.data.model.MediaRequest

/** Above the field while ":name" is being typed: the room's custom emoji it matches. */
@Composable
internal fun EmoteSuggestions(emotes: List<CustomEmote>, onPick: (CustomEmote) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp)
    ) {
        items(emotes, key = { it.shortcode }) { emote ->
            AssistChip(
                onClick = { onPick(emote) },
                label = { Text(":${emote.shortcode}:", maxLines = 1) },
                leadingIcon = {
                    // Decorative: the label names it.
                    AsyncImage(
                        model = MediaRequest(emote.image, EMOTE_PX),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )
        }
    }
}
