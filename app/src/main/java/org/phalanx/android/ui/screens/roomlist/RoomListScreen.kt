package org.phalanx.android.ui.screens.roomlist

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.phalanx.android.R
import org.phalanx.android.data.model.RoomSummary
import org.phalanx.android.ui.components.InitialAvatar
import org.phalanx.android.ui.components.RoomListPlaceholder
import org.phalanx.android.ui.components.SegmentGap
import org.phalanx.android.ui.components.segmentedShape
import org.phalanx.android.ui.screens.search.SearchOverlay
import org.phalanx.android.ui.theme.Symbols

@Composable
fun RoomListScreen(
    onOpenRoom: (String) -> Unit,
    onOpenDetails: (String) -> Unit,
    onOpenAccount: () -> Unit,
    viewModel: RoomListViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val avatar by viewModel.avatar.collectAsStateWithLifecycle()
    val actionFailed by viewModel.actionFailed.collectAsStateWithLifecycle()
    var menuFor by remember { mutableStateOf<RoomSummary?>(null) }
    var leaving by remember { mutableStateOf<RoomSummary?>(null) }
    var searching by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val failure = stringResource(R.string.timeline_action_failed)
    LaunchedEffect(actionFailed) {
        if (actionFailed) {
            snackbar.showSnackbar(failure)
            viewModel.dismissFailure()
        }
    }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            // One row: the title and the account button share it, so there is no empty band above
            // the title as with a large app bar.
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.roomlist_title),
                        style = MaterialTheme.typography.headlineLarge
                    )
                },
                actions = {
                    IconButton(onClick = { searching = true }) {
                        Icon(Symbols.Search, contentDescription = stringResource(R.string.search_open))
                    }
                    AccountButton(profile?.label.orEmpty(), avatar, onOpenAccount)
                },
                expandedHeight = 72.dp,
                scrollBehavior = scrollBehavior
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        // Keyed on the kind of state, so the skeleton fades into the list once, not on every update.
        val fade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        AnimatedContent(
            targetState = state,
            contentKey = { it::class },
            transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) }
        ) { current ->
            when (current) {
                RoomListUiState.Loading -> RoomListPlaceholder(padding)
                is RoomListUiState.Loaded ->
                    RoomList(current, padding, viewModel::setFilter, onOpenRoom, onLongClick = { menuFor = it })
            }
        }
    }

    // Grows out of the search button's corner, the way a search bar expands in Material 3.
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = searching,
        enter = fadeIn(motion.defaultEffectsSpec()) +
            scaleIn(motion.defaultSpatialSpec(), initialScale = SEARCH_FROM_SCALE, transformOrigin = SearchOrigin),
        exit = fadeOut(motion.fastEffectsSpec()) +
            scaleOut(motion.fastSpatialSpec(), targetScale = SEARCH_FROM_SCALE, transformOrigin = SearchOrigin)
    ) {
        SearchOverlay(onClose = { searching = false }, onOpenRoom = onOpenRoom)
    }

    menuFor?.let { room ->
        fun then(action: () -> Unit): () -> Unit = {
            action()
            menuFor = null
        }
        RoomActionsSheet(
            room = room,
            onDismiss = { menuFor = null },
            onToggleRead = then { viewModel.setUnread(room, !room.hasUnread) },
            onToggleFavourite = then { viewModel.setFavourite(room, !room.isFavourite) },
            onToggleArchived = then { viewModel.setArchived(room, !room.isLowPriority) },
            onDetails = then { onOpenDetails(room.id) },
            onLeave = then { leaving = room }
        )
    }
    leaving?.let { room ->
        LeaveRoomDialog(
            name = room.name,
            onConfirm = {
                viewModel.leave(room)
                leaving = null
            },
            onDismiss = { leaving = null }
        )
    }
}

@Composable
private fun AccountButton(label: String, avatar: ImageBitmap?, onClick: () -> Unit) {
    val description = stringResource(R.string.roomlist_account)
    IconButton(onClick = onClick, modifier = Modifier.padding(end = 8.dp).size(48.dp)) {
        InitialAvatar(
            name = label,
            shape = CircleShape,
            size = 40.dp,
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer,
            image = avatar,
            textStyle = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { contentDescription = description }
        )
    }
}

@Composable
private fun RoomList(
    state: RoomListUiState.Loaded,
    padding: PaddingValues,
    onFilter: (RoomFilter) -> Unit,
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
            RoomFilterRow(state.filter, state.archivedCount, onFilter)
        }
        if (rooms.isEmpty()) {
            item(key = EMPTY_KEY, contentType = EMPTY_KEY) {
                Text(
                    text = stringResource(
                        if (state.filter == RoomFilter.All) R.string.roomlist_empty else R.string.roomlist_filter_empty
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
private const val SEARCH_FROM_SCALE = 0.85f
private val SearchOrigin = TransformOrigin(1f, 0f)
private const val FILTERS_KEY = "filters"
private const val EMPTY_KEY = "empty"
private const val INTRO_MILLIS = 600L
