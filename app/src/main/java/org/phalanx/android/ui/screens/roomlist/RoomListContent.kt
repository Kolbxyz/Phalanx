package org.phalanx.android.ui.screens.roomlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.phalanx.android.R
import org.phalanx.android.data.model.RoomSummary
import org.phalanx.android.ui.components.SegmentGap
import org.phalanx.android.ui.components.segmentedShape

/** The loaded rooms under their filter row, each sliding into place as the list changes. */
@Composable
internal fun RoomList(
    state: RoomListUiState.Loaded,
    drafts: Map<String, String>,
    padding: PaddingValues,
    onFilter: (RoomFilter) -> Unit,
    onClearSource: () -> Unit,
    onOpenRoom: (String) -> Unit,
    onLongClick: (RoomSummary) -> Unit
) {
    val motion = MaterialTheme.motionScheme
    val rooms = state.rooms
    // Rows composed in the list's first moments get the staggered entrance; later ones don't.
    var intro by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(INTRO_MILLIS)
        intro = false
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding() + 4.dp,
            bottom = padding.calculateBottomPadding() + 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(SegmentGap)
    ) {
        item(key = FILTERS_KEY, contentType = FILTERS_KEY) {
            RoomFilterRow(state.filter, state.archivedCount, onFilter, state.source, onClearSource)
        }
        if (rooms.isEmpty()) {
            item(key = EMPTY_KEY, contentType = EMPTY_KEY) {
                Text(
                    text = stringResource(
                        if (state.filter == RoomFilter.All && state.source == RoomSource.All) {
                            R.string.roomlist_empty
                        } else {
                            R.string.roomlist_filter_empty
                        }
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 64.dp).animateItem(
                        fadeInSpec = motion.defaultEffectsSpec(),
                        placementSpec = motion.defaultSpatialSpec(),
                        fadeOutSpec = motion.defaultEffectsSpec()
                    )
                )
            }
        }
        itemsIndexed(rooms, key = { _, room -> room.id }, contentType = { _, _ -> ROOM_ROW }) { index, room ->
            RoomRow(
                room,
                draft = drafts[room.id],
                shape = segmentedShape(index, rooms.size),
                onClick = { onOpenRoom(room.id) },
                onLongClick = { onLongClick(room) },
                // A room that gets a new message slides to the top instead of jumping there, and
                // switching filters fades rooms out and in while the rest slide into place.
                modifier = Modifier
                    .animateItem(
                        fadeInSpec = motion.defaultEffectsSpec(),
                        placementSpec = motion.defaultSpatialSpec(),
                        fadeOutSpec = motion.defaultEffectsSpec()
                    )
                    .listEntrance(index, intro)
            )
        }
    }
}

private const val ROOM_ROW = 0
private const val FILTERS_KEY = "filters"
private const val EMPTY_KEY = "empty"
private const val INTRO_MILLIS = 600L
