package org.phalanx.android.ui.screens.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.phalanx.android.data.model.TimelineEntry

/**
 * The newest photos and videos as one rounded block: the newest twice the size, the rest in rows of
 * three. Tiles are cut tight and the block round, so the grid reads as one shape cut into tiles.
 */
@Composable
internal fun MediaMosaic(items: List<TimelineEntry.Message>, onOpen: (TimelineEntry.Message) -> Unit) {
    if (items.isEmpty()) return
    val gap = Arrangement.spacedBy(GAP)
    BoxWithConstraints(Modifier.fillMaxWidth().clip(Outer)) {
        val cell = (maxWidth - GAP * (COLUMNS - 1)) / COLUMNS
        Column(verticalArrangement = gap) {
            Row(horizontalArrangement = gap) {
                MediaTile(items.first(), Modifier.size(cell * 2 + GAP), onOpen, Inner)
                Column(verticalArrangement = gap) {
                    items.drop(1).take(2).forEach { MediaTile(it, Modifier.size(cell), onOpen, Inner) }
                }
            }
            items.drop(LEAD_BLOCK).chunked(COLUMNS).forEach { row ->
                Row(horizontalArrangement = gap) {
                    row.forEach { MediaTile(it, Modifier.size(cell), onOpen, Inner) }
                }
            }
        }
    }
}

// The newest and the two beside it.
private const val LEAD_BLOCK = 3
private const val COLUMNS = 3
private val GAP = 4.dp

private val Outer = RoundedCornerShape(24.dp)
private val Inner = RoundedCornerShape(6.dp)
