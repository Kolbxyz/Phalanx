package org.phalanx.android.ui.screens.room

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.phalanx.android.R
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.ui.components.pressShrink
import org.phalanx.android.ui.theme.LocalReducedMotion
import org.phalanx.android.ui.theme.Symbols

private const val LOAD_AHEAD = 5
private const val OLDER_KEY = "older"
private const val JUMP_AFTER_ITEMS = 4
private const val FOCUS_OFFSET_DIVISOR = 3

// Messages further apart than this start a new run even from the same sender.
private const val RUN_GAP_MILLIS = 5 * 60 * 1000L

@Composable
internal fun TimelineList(
    state: RoomUiState.Ready,
    padding: PaddingValues,
    actions: MessageActions,
    onLoadOlder: () -> Unit,
    focus: String?,
    onFocused: () -> Unit
) {
    val listState = rememberLazyListState()
    val motion = MaterialTheme.motionScheme
    val reducedMotion = LocalReducedMotion.current
    // Whether the list sticks to the newest message as they arrive; see below. Saved, like the
    // scroll position, for when the conversation comes back from the media viewer.
    var following by rememberSaveable { mutableStateOf(true) }

    // A message asked for from elsewhere (a pinned message, a photo, a search hit): brought a little
    // above the middle and highlighted, so the eye finds it.
    var highlighted by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(focus) {
        val target = focus ?: return@LaunchedEffect
        val index = state.entries.indexOfFirst { it.id == target }
        if (index >= 0) {
            val offset = -listState.layoutInfo.viewportSize.height / FOCUS_OFFSET_DIVISOR
            following = false
            if (reducedMotion) listState.scrollToItem(index, offset) else listState.animateScrollToItem(index, offset)
            highlighted = target
        }
        onFocused()
    }

    // The list is reversed, so the oldest message is the last item; ask for more history when it
    // gets close, and again each time the list grows and it is still close.
    LaunchedEffect(listState, state.canLoadOlder) {
        if (!state.canLoadOlder) return@LaunchedEffect
        // Keyed on the item count too: a snapshot of just "near the top" would not change after a
        // page that still leaves the list short, and no further page would be asked for.
        snapshotFlow {
            val info = listState.layoutInfo
            val nearTop = (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - LOAD_AHEAD
            if (nearTop) info.totalItemsCount else null
        }.filterNotNull().collect { onLoadOlder() }
    }

    // A message arriving while the user reads the newest ones should come into view, not land
    // below the fold because the list kept its anchor on the previous item. Whether the user is
    // following the newest messages is only decided when a scroll ends, so a burst of arrivals
    // can't push the list out of following by itself. A message this account sends always shows.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { scrolling -> !scrolling }
            .collect { following = listState.firstVisibleItemIndex <= 1 }
    }
    val newest = state.entries.firstOrNull()
    // Only a message that arrived since the list was last shown scrolls it: coming back to the
    // conversation must not drop the reader at the bottom.
    var shownNewest by rememberSaveable { mutableStateOf(newest?.id) }
    LaunchedEffect(newest?.id) {
        if (newest?.id == shownNewest) return@LaunchedEffect
        shownNewest = newest?.id
        if (following || (newest as? TimelineEntry.Message)?.isOwn == true) {
            if (reducedMotion) listState.scrollToItem(0) else listState.animateScrollToItem(0)
        }
    }
    // Rows present when the conversation opened (and older pages) just appear; later ones animate in.
    val initialIds = remember { state.entries.mapTo(HashSet()) { it.id } }
    val reveal = remember { TimestampReveal() }

    val entries by rememberUpdatedState(state.entries)
    val voiceChain = remember { VoiceChain { id -> nextVoiceAfter(entries, id) } }
    VoiceChainScroll(voiceChain, listState, entries, reducedMotion)

    val scope = rememberCoroutineScope()
    val showJump by remember { derivedStateOf { listState.firstVisibleItemIndex > JUMP_AFTER_ITEMS } }
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalTimestampReveal provides reveal, LocalVoiceChain provides voiceChain) {
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
                    val isNew = index == 0 && entry.id !in initialIds
                    val animated = Modifier.animateItem(
                        fadeInSpec = if (isNew) null else motion.defaultEffectsSpec(),
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
                                isNew = isNew,
                                actions = actions,
                                modifier = animated.focusHighlight(entry.id == highlighted) { highlighted = null }
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
        FloatingDate(
            listState = listState,
            entries = state.entries,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = padding.calculateTopPadding() + 8.dp)
        )
        // Pinned over the list rather than a row in it, so it stays in view however far up the reader is.
        AnimatedVisibility(
            visible = state.typing.isNotEmpty(),
            enter = slideInVertically(motion.defaultSpatialSpec()) { it } + fadeIn(motion.defaultEffectsSpec()),
            exit = slideOutVertically(motion.defaultSpatialSpec()) { it } + fadeOut(motion.defaultEffectsSpec()),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = padding.calculateBottomPadding() + 8.dp)
        ) {
            // Kept through the exit animation, when the list itself is already empty.
            var last by remember { mutableStateOf(state.typing) }
            if (state.typing.isNotEmpty()) last = state.typing
            TypingIndicator(last, isGroupChat = !state.isDirect)
        }
        JumpToLatest(
            visible = showJump,
            onClick = {
                scope.launch { if (reducedMotion) listState.scrollToItem(0) else listState.animateScrollToItem(0) }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = padding.calculateBottomPadding() + 16.dp)
        )
    }
}

private fun sameRun(earlier: TimelineEntry?, later: TimelineEntry?): Boolean {
    if (earlier !is TimelineEntry.Message || later !is TimelineEntry.Message) return false
    return earlier.senderId == later.senderId && later.timestampMillis - earlier.timestampMillis < RUN_GAP_MILLIS
}

/** Back to the newest message, shown once the user has scrolled up a little. */
@Composable
private fun JumpToLatest(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(motion.fastSpatialSpec()) + fadeIn(motion.fastEffectsSpec()),
        exit = scaleOut(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
        modifier = modifier
    ) {
        val interaction = remember { MutableInteractionSource() }
        SmallFloatingActionButton(
            onClick = onClick,
            modifier = Modifier.pressShrink(interaction),
            interactionSource = interaction,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Icon(Symbols.KeyboardArrowDown, contentDescription = stringResource(R.string.timeline_jump_latest))
        }
    }
}
