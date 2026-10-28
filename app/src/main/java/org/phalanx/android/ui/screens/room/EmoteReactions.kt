package org.phalanx.android.ui.screens.room

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.phalanx.android.data.model.CustomEmote
import org.phalanx.android.data.model.MediaRequest
import org.phalanx.android.data.model.TimelineEntry

/**
 * The room's custom emoji under the quick reactions, scrolling sideways. A reaction with one is
 * keyed by its image's URL, which bridges turn back into the network's own emoji.
 */
@Composable
internal fun EmoteReactions(message: TimelineEntry.Message, emotes: List<CustomEmote>, onReact: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(emotes, key = { it.url }) { emote ->
            val mine = message.reactions.any { it.key == emote.url && it.byMe }
            Surface(
                onClick = { onReact(emote.url) },
                shape = MaterialShapes.Cookie6Sided.toShape(),
                color = if (mine) colors.secondaryContainer else colors.surfaceContainerHigh,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = MediaRequest(emote.image, EMOTE_PX),
                        contentDescription = ":${emote.shortcode}:",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}
