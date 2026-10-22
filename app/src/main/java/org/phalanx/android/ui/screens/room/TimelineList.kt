package org.phalanx.android.ui.screens.room

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.phalanx.android.R
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.ui.theme.LocalReducedMotion
import org.phalanx.android.ui.theme.Symbols
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val LOAD_AHEAD = 5
private const val OLDER_KEY = "older"
private const val TYPING_KEY = "typing"
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
    // Whether the list sticks to the newest message as they arrive; see below.
    var following by remember { mutableStateOf(true) }

    // A message asked for from elsewhere (a pinned message, a photo, a search hit): brought a little
    // above the middle and highlighted, so the eye finds it.
    var highlighted by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(focus) {
        val target = focus ?: return@LaunchedEffect
        val index = state.entries.indexOfFirst { it.id == target }
        if (index >= 0) {
            // The typing row, when shown, comes before every entry in the list.
            val item = index + if (state.typing.isNotEmpty()) 1 else 0
            val offset = -listState.layoutInfo.viewportSize.height / FOCUS_OFFSET_DIVISOR
            following = false
            if (reducedMotion) listState.scrollToItem(item, offset) else listState.animateScrollToItem(item, offset)
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
    LaunchedEffect(newest?.id) {
        if (following || (newest as? TimelineEntry.Message)?.isOwn == true) {
            if (reducedMotion) listState.scrollToItem(0) else listState.animateScrollToItem(0)
        }
    }
    // Rows present when the conversation opened (and older pages) just appear; later ones animate in.
    val initialIds = remember { state.entries.mapTo(HashSet()) { it.id } }
    val reveal = remember { TimestampReveal() }

    val scope = rememberCoroutineScope()
    val showJump by remember { derivedStateOf { listState.firstVisibleItemIndex > JUMP_AFTER_ITEMS } }
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalTimestampReveal provides reveal) {
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
                // First in a reversed list, so it sits under the newest message.
                if (state.typing.isNotEmpty()) {
                    item(key = TYPING_KEY, contentType = TYPING_KEY) {
                        TypingIndicator(
                            names = state.typing,
                            isGroupChat = !state.isDirect,
                            modifier = Modifier.animateItem(
                                fadeInSpec = motion.defaultEffectsSpec(),
                                placementSpec = motion.defaultSpatialSpec(),
                                fadeOutSpec = motion.defaultEffectsSpec()
                            )
                        )
                    }
                }
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

/** A centred date chip: "Today", "Yesterday", the weekday within a week, otherwise the date. */
@Composable
private fun DayDivider(millis: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val label = remember(millis) { dayLabel(context, millis) }
    Box(modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .semantics { heading() }
            )
        }
    }
}

private fun dayLabel(context: Context, millis: Long): String {
    val zone = ZoneId.systemDefault()
    val day = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    val locale = context.resources.configuration.locales[0]
    val skeleton = when {
        day == today || day == today.minusDays(1) -> null
        day.isAfter(today.minusDays(WEEK_DAYS)) -> "EEEE"
        day.year == today.year -> "EEEdMMMM"
        else -> "EEEdMMMMyyyy"
    }
    return when (skeleton) {
        null -> context.getString(if (day == today) R.string.timeline_today else R.string.timeline_yesterday)
        else -> DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale).format(day)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }
}

private const val WEEK_DAYS = 6L

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
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Icon(Symbols.KeyboardArrowDown, contentDescription = stringResource(R.string.timeline_jump_latest))
        }
    }
}
