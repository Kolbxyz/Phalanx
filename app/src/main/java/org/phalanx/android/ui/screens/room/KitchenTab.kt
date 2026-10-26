package org.phalanx.android.ui.screens.room

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.phalanx.android.R
import org.phalanx.android.data.model.KitchenMix
import org.phalanx.android.data.model.emojiOf
import org.phalanx.android.ui.theme.Symbols

private const val PICKED_SCALE = 1.12f
private val PickSize = 64.dp

/**
 * Starred and recent mixes first, then an emoji picked in the strip (searchable by name) and its
 * mixes in the grid; a tap sends, a long press stars. Strip and grid sit on tonal containers,
 * grouped as Expressive button groups are, the picked emoji on a cookie shape.
 */
@Composable
internal fun KitchenTab(viewModel: KitchenViewModel, onPick: (KitchenMix) -> Unit, onStarFailed: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val bases by produceState(emptyList<String>(), query) { value = viewModel.bases(query) }
    var base by rememberSaveable { mutableStateOf<String?>(null) }
    // A search moves the pick to its first match, so the grid always shows something the strip has.
    LaunchedEffect(bases) { if (base !in bases) base = bases.firstOrNull() }
    val mixes by produceState(emptyList<KitchenMix>(), base) { value = base?.let { viewModel.mixesOf(it) }.orEmpty() }
    val picks by viewModel.picks.collectAsStateWithLifecycle()
    val starred = picks.favourites.toSet()
    val toggleStar: (KitchenMix) -> Unit = { viewModel.toggleFavourite(it, onStarFailed) }
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Picks(R.string.kitchen_starred, picks.favourites, viewModel, onPick, starred, toggleStar)
        Picks(R.string.kitchen_recent, picks.recent.filterNot { it in starred }, viewModel, onPick, starred, toggleStar)
        TextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.kitchen_search)) },
            leadingIcon = { Icon(Symbols.Search, contentDescription = null) },
            singleLine = true,
            shape = CircleShape,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = colors.surfaceContainerHigh,
                unfocusedContainerColor = colors.surfaceContainerHigh
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        Surface(
            shape = CircleShape,
            color = colors.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(80.dp),
                modifier = Modifier.heightIn(max = 360.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(mixes, key = { it.left + "_" + it.right }) { mix ->
                    MixTile(
                        mix, viewModel, onClick = { onPick(mix) },
                        starred = mix in starred, onLongClick = { toggleStar(mix) }
                    )
                }
            }
        }
    }
}

/** A titled row of mixes, left out when there are none. */
@Composable
private fun Picks(
    @StringRes title: Int,
    mixes: List<KitchenMix>,
    viewModel: KitchenViewModel,
    onPick: (KitchenMix) -> Unit,
    starred: Set<KitchenMix>,
    onToggleStar: (KitchenMix) -> Unit
) {
    if (mixes.isEmpty()) return
    Text(
        stringResource(title),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp)
    )
    MixRow(mixes, viewModel, PickSize, onPick, PaddingValues(horizontal = 16.dp), starred, onToggleStar)
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
