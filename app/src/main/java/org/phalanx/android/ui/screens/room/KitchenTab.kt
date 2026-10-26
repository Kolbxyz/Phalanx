package org.phalanx.android.ui.screens.room

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.phalanx.android.data.model.KitchenMix
import org.phalanx.android.data.model.emojiOf

private const val PICKED_SCALE = 1.12f

/**
 * Pick an emoji in the row, then one of its mixes in the grid; a tap sends it. Both sit on tonal
 * containers, grouped as Expressive button groups are, the picked emoji on a cookie shape.
 */
@Composable
internal fun KitchenTab(viewModel: KitchenViewModel, onPick: (KitchenMix) -> Unit) {
    val bases by produceState(emptyList<String>()) { value = viewModel.bases() }
    var base by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(bases) { if (base == null) base = bases.firstOrNull() }
    val mixes by produceState(emptyList<KitchenMix>(), base) { value = base?.let { viewModel.mixesOf(it) }.orEmpty() }
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(shape = CircleShape, color = colors.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
            LazyRow(
                contentPadding = PaddingValues(6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(bases, key = { it }) { codepoints ->
                    BaseButton(codepoints, picked = codepoints == base) { base = codepoints }
                }
            }
        }
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = colors.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(80.dp),
                modifier = Modifier.heightIn(max = 360.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(mixes, key = { it.left + "_" + it.right }) { mix ->
                    MixCell(mix, viewModel) { onPick(mix) }
                }
            }
        }
    }
}

@Composable
private fun BaseButton(codepoints: String, picked: Boolean, onClick: () -> Unit) {
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (picked) colors.primaryContainer else colors.surfaceContainerHigh,
        motion.defaultEffectsSpec()
    )
    val scale by animateFloatAsState(if (picked) PICKED_SCALE else 1f, motion.fastSpatialSpec())
    Surface(
        onClick = onClick,
        shape = if (picked) MaterialShapes.Cookie9Sided.toShape() else CircleShape,
        color = container,
        modifier = Modifier.size(48.dp).graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(emojiOf(codepoints), style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun MixCell(mix: KitchenMix, viewModel: KitchenViewModel, onClick: () -> Unit) {
    // Loaded only as the cell scrolls into view, so opening the tab fetches one screenful at most.
    val state by produceState<Any?>(Loading, mix) { value = viewModel.image(mix) }
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.aspectRatio(1f)
    ) {
        Box(Modifier.padding(6.dp), contentAlignment = Alignment.Center) {
            when (val file = state) {
                Loading -> LoadingIndicator(Modifier.size(32.dp))
                null -> Text(mix.body, style = MaterialTheme.typography.titleLarge)
                else -> AsyncImage(model = file, contentDescription = mix.body, modifier = Modifier.aspectRatio(1f))
            }
        }
    }
}

private object Loading
