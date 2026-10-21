package org.phalanx.android.ui.screens.room

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.phalanx.android.R
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.ui.components.TimelinePlaceholder
import org.phalanx.android.ui.theme.LocalReducedMotion
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun RoomScreen(onBack: () -> Unit, viewModel: RoomViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val sendFailed by viewModel.sendFailed.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val failedMessage = stringResource(R.string.composer_send_failed)

    LaunchedEffect(sendFailed) {
        if (sendFailed) {
            snackbar.showSnackbar(failedMessage)
            viewModel.dismissSendFailed()
        }
    }

    val ready = state as? RoomUiState.Ready
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = ready?.title.orEmpty(), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        bottomBar = {
            if (ready != null) Composer(draft, viewModel::onDraftChange, viewModel::send)
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        val fade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        AnimatedContent(
            targetState = state,
            contentKey = { it::class },
            transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) }
        ) { current ->
            when (current) {
                RoomUiState.Loading -> TimelinePlaceholder(padding)
                RoomUiState.Unavailable -> Centered(padding) {
                    Text(
                        text = stringResource(R.string.room_unavailable),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is RoomUiState.Ready -> Timeline(current, padding, viewModel::loadOlder)
            }
        }
    }
}

@Composable
private fun Centered(padding: PaddingValues, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { content() }
}

@Composable
private fun Timeline(state: RoomUiState.Ready, padding: PaddingValues, onLoadOlder: () -> Unit) {
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
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(state.entries, key = { it.id }, contentType = { it::class }) { entry ->
            val animated = Modifier.animateItem(
                fadeInSpec = motion.defaultEffectsSpec(),
                placementSpec = motion.defaultSpatialSpec(),
                fadeOutSpec = motion.defaultEffectsSpec()
            )
            when (entry) {
                is TimelineEntry.Message -> MessageBubble(entry, showSender = !state.isDirect, modifier = animated)
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

@Composable
private fun DayDivider(millis: Long, modifier: Modifier = Modifier) {
    val label = remember(millis) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
    }
    Text(
        text = label,
        modifier = modifier.fillMaxWidth().padding(vertical = 12.dp),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private const val LOAD_AHEAD = 5
private const val OLDER_KEY = "older"
