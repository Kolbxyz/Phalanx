package org.phalanx.android.ui.screens.room

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.phalanx.android.data.model.KitchenMix
import org.phalanx.android.data.model.emojiOf

/** Pick an emoji in the row, then one of its mixes in the grid; a tap sends it. */
@Composable
internal fun KitchenTab(viewModel: KitchenViewModel, onPick: (KitchenMix) -> Unit) {
    val bases by produceState(emptyList<String>()) { value = viewModel.bases() }
    var base by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(bases) { if (base == null) base = bases.firstOrNull() }
    val mixes by produceState(emptyList<KitchenMix>(), base) { value = base?.let { viewModel.mixesOf(it) }.orEmpty() }
    Column {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(bases, key = { it }) { codepoints ->
                val colors = MaterialTheme.colorScheme
                Surface(
                    shape = CircleShape,
                    color = if (codepoints == base) colors.secondaryContainer else colors.surface,
                    modifier = Modifier.size(44.dp).clickable { base = codepoints }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(emojiOf(codepoints), style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(80.dp),
            modifier = Modifier
                .heightIn(max = 360.dp)
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(mixes, key = { it.left + "_" + it.right }) { mix ->
                MixCell(mix, viewModel, Modifier.aspectRatio(1f).clickable { onPick(mix) })
            }
        }
    }
}

@Composable
private fun MixCell(mix: KitchenMix, viewModel: KitchenViewModel, modifier: Modifier) {
    // Loaded only as the cell scrolls into view, so opening the tab fetches one screenful at most.
    val state by produceState<Any?>(Loading, mix) { value = viewModel.image(mix) }
    Box(modifier, contentAlignment = Alignment.Center) {
        when (val file = state) {
            Loading -> LoadingIndicator(Modifier.size(32.dp))
            null -> Text(mix.body, style = MaterialTheme.typography.titleLarge)
            else -> AsyncImage(model = file, contentDescription = mix.body, modifier = Modifier.aspectRatio(1f))
        }
    }
}

private object Loading
