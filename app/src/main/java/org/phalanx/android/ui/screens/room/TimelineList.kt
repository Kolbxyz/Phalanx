package org.phalanx.android.ui.screens.room

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.ui.theme.LocalReducedMotion
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val LOAD_AHEAD = 5
private const val OLDER_KEY = "older"

// Messages further apart than this start a new run even from the same sender.
private const val RUN_GAP_MILLIS = 5 * 60 * 1000L

@Composable
internal fun TimelineList(
    state: RoomUiState.Ready,
    padding: PaddingValues,
    actions: MessageActions,
    onLoadOlder: () -> Unit
) {
    val listState = rememberLazyListState()
    val motion = MaterialTheme.motionScheme

    // The list is reversed, so the oldest message is the last item; ask for more history when it
    // gets close, and again each time the list grows and it is still close.
    LaunchedEffect(listState, state.canLoadOlder) {
        if (!state.canLoadOlder) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - LOAD_AHEAD
        }.collect { nearTop -> if (nearTop) onLoadOlder() }
    }

    // A message arriving while the user reads the newest ones should come into view, not land
    // below the fold because the list kept its anchor on the previous item.
    val reducedMotion = LocalReducedMotion.current
    val newestId = state.entries.firstOrNull()?.id
    LaunchedEffect(newestId) {
        if (listState.firstVisibleItemIndex <= 1) {
            if (reducedMotion) listState.scrollToItem(0) else listState.animateScrollToItem(0)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        reverseLayout = true,
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 8.dp
        ),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        itemsIndexed(
            items = state.entries,
            key = { _, entry -> entry.id },
            contentType = { _, entry -> entry::class }
        ) { index, entry ->
            val animated = Modifier.animateItem(
                fadeInSpec = motion.defaultEffectsSpec(),
                placementSpec = motion.defaultSpatialSpec(),
                fadeOutSpec = motion.defaultEffectsSpec()
            )
            when (entry) {
                is TimelineEntry.Message -> {
                    // Newest first: the item after this one is older, the one before is newer.
                    val older = state.entries.getOrNull(index + 1)
                    val newer = state.entries.getOrNull(index - 1)
                    MessageBubble(
                        message = entry,
                        isGroupChat = !state.isDirect,
                        startsRun = !sameRun(older, entry),
                        endsRun = !sameRun(entry, newer),
                        actions = actions,
                        modifier = animated
                    )
                }
                is TimelineEntry.DayDivider -> DayDivider(entry.millis, animated)
            }
        }
        // Last in a reversed list, so it sits above the oldest message while history loads.
        if (state.canLoadOlder) {
            item(key = OLDER_KEY, contentType = OLDER_KEY) {
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), Alignment.Center) {
                    LoadingIndicator(Modifier.size(32.dp))
                }
            }
        }
    }
}

private fun sameRun(earlier: TimelineEntry?, later: TimelineEntry?): Boolean {
    if (earlier !is TimelineEntry.Message || later !is TimelineEntry.Message) return false
    return earlier.senderId == later.senderId && later.timestampMillis - earlier.timestampMillis < RUN_GAP_MILLIS
}

@Composable
private fun DayDivider(millis: Long, modifier: Modifier = Modifier) {
    val label = remember(millis) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
    }
    Text(
        text = label,
        modifier = modifier.fillMaxWidth().padding(vertical = 12.dp),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
