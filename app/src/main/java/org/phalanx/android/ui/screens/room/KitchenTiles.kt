package org.phalanx.android.ui.screens.room

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.phalanx.android.R
import org.phalanx.android.data.model.KitchenMix
import org.phalanx.android.ui.theme.Symbols

/**
 * One mix on a tonal tile: its image once fetched, the two emoji if it can't be. Loaded only as
 * the tile comes into view, so a grid fetches one screenful at most. [starred] shows a star badge.
 */
@Composable
internal fun MixTile(
    mix: KitchenMix,
    viewModel: KitchenViewModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    starred: Boolean = false,
    onLongClick: (() -> Unit)? = null
) {
    val state by produceState<Any?>(Loading, mix) { value = viewModel.image(mix) }
    val shape = MaterialTheme.shapes.large
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.aspectRatio(1f)) {
        Box(
            Modifier.clip(shape).combinedClickable(
                onClickLabel = stringResource(R.string.composer_kitchen_send),
                onLongClickLabel = onLongClick?.let {
                    stringResource(if (starred) R.string.kitchen_unstar else R.string.kitchen_star)
                },
                onLongClick = onLongClick,
                onClick = onClick
            ).padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            when (val file = state) {
                Loading -> LoadingIndicator(Modifier.size(32.dp))
                null -> Text(mix.body, style = MaterialTheme.typography.titleLarge)
                else -> AsyncImage(model = file, contentDescription = mix.body, modifier = Modifier.aspectRatio(1f))
            }
            if (starred) {
                Icon(
                    Symbols.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.align(Alignment.TopEnd).size(16.dp)
                )
            }
        }
    }
}

/** A row of mix tiles, as the composer's choices for one typed emoji; null hides it. */
@Composable
internal fun KitchenChoices(mixes: List<KitchenMix>?, onSend: (KitchenMix) -> Unit, kitchen: KitchenViewModel) {
    val motion = MaterialTheme.motionScheme
    // The last row stays drawn while it collapses, so it doesn't blank out.
    var shown by remember { mutableStateOf(emptyList<KitchenMix>()) }
    if (!mixes.isNullOrEmpty()) shown = mixes
    AnimatedVisibility(
        visible = !mixes.isNullOrEmpty(),
        enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
        exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.defaultEffectsSpec())
    ) {
        MixRow(shown, kitchen, ChoiceSize, onSend, PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp))
    }
}

/** Mix tiles of [size] in a row, as the sheet's recent and starred ones and the composer's choices. */
@Composable
internal fun MixRow(
    mixes: List<KitchenMix>,
    kitchen: KitchenViewModel,
    size: Dp,
    onPick: (KitchenMix) -> Unit,
    contentPadding: PaddingValues,
    starred: Set<KitchenMix> = emptySet(),
    onToggleStar: ((KitchenMix) -> Unit)? = null
) {
    LazyRow(contentPadding = contentPadding, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(mixes, key = { it.left + "_" + it.right }) { mix ->
            MixTile(
                mix, kitchen, onClick = { onPick(mix) }, modifier = Modifier.size(size),
                starred = mix in starred, onLongClick = onToggleStar?.let { { it(mix) } }
            )
        }
    }
}

private val ChoiceSize = 56.dp

private object Loading
