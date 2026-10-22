package org.phalanx.android.ui.screens.room

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.phalanx.android.R
import org.phalanx.android.core.media.StickerCatalog
import org.phalanx.android.data.model.Sticker
import org.phalanx.android.data.model.StickerPack
import org.phalanx.android.ui.components.ModalSheet

/** The composer's sticker picker: a row of packs over the grid of the chosen one; a tap sends. */
@Composable
internal fun StickerSheet(
    loadPacks: suspend () -> List<StickerPack>,
    onDismiss: () -> Unit,
    onPick: (Sticker) -> Unit
) {
    var packs by remember { mutableStateOf<List<StickerPack>>(emptyList()) }
    var selected by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { packs = loadPacks() }
    ModalSheet(onDismissRequest = onDismiss) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            packs.forEachIndexed { index, pack ->
                FilterChip(
                    selected = index == selected,
                    onClick = { selected = index },
                    label = { Text(stringResource(titleOf(pack))) }
                )
            }
        }
        packs.getOrNull(selected)?.let { pack ->
            LazyVerticalGrid(
                columns = GridCells.Adaptive(88.dp),
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(pack.stickers, key = { it.assetPath }) { sticker ->
                    AsyncImage(
                        model = "file:///android_asset/${StickerCatalog.DIR}/${sticker.assetPath}",
                        contentDescription = sticker.body,
                        modifier = Modifier.aspectRatio(1f).clickable { onPick(sticker) }
                    )
                }
            }
        }
    }
}

@StringRes
private fun titleOf(pack: StickerPack): Int = when (pack.id) {
    "reactions" -> R.string.sticker_pack_reactions
    "love" -> R.string.sticker_pack_love
    "cats" -> R.string.sticker_pack_cats
    "snacks" -> R.string.sticker_pack_snacks
    "france" -> R.string.sticker_pack_france
    else -> error("No title for sticker pack ${pack.id}")
}
