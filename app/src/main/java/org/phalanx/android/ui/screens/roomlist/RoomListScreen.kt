package org.phalanx.android.ui.screens.roomlist

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.phalanx.android.R
import org.phalanx.android.data.model.RoomSummary
import org.phalanx.android.ui.components.RoomListPlaceholder
import org.phalanx.android.ui.screens.search.SearchOverlay
import org.phalanx.android.ui.theme.Symbols

@Composable
fun RoomListScreen(
    onOpenRoom: (String) -> Unit,
    onOpenMessage: (roomId: String, eventId: String) -> Unit,
    onOpenDetails: (String) -> Unit,
    onOpenAccount: () -> Unit,
    onOpenCatchUp: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenPeople: () -> Unit,
    viewModel: RoomListViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val avatar by viewModel.avatar.collectAsStateWithLifecycle()
    val actionFailed by viewModel.actionFailed.collectAsStateWithLifecycle()
    val drafts by viewModel.drafts.collectAsStateWithLifecycle()
    val canCatchUp by viewModel.canCatchUp.collectAsStateWithLifecycle()
    val reminders by viewModel.reminderCount.collectAsStateWithLifecycle()
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
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val loaded = state as? RoomListUiState.Loaded
    SourcesDrawer(
        drawerState = drawer,
        sources = loaded?.sources.orEmpty(),
        selected = loaded?.source ?: RoomSource.All,
        onSelect = { source ->
            viewModel.setSource(source)
            scope.launch { drawer.close() }
        },
        reminders = reminders,
        onOpenReminders = {
            scope.launch { drawer.close() }
            onOpenReminders()
        },
        onOpenBookmarks = {
            scope.launch { drawer.close() }
            onOpenBookmarks()
        },
        onOpenPeople = {
            scope.launch { drawer.close() }
            onOpenPeople()
        }
    ) {
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
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawer.open() } }, enabled = loaded != null) {
                            Icon(Symbols.Hub, contentDescription = stringResource(R.string.sources_open))
                        }
                    },
                    actions = {
                        if (canCatchUp) {
                            IconButton(onClick = onOpenCatchUp) {
                                Icon(Symbols.Summarize, contentDescription = stringResource(R.string.catch_up_open))
                            }
                        }
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
                    is RoomListUiState.Loaded -> RoomList(
                        current,
                        drafts,
                        padding,
                        viewModel::setFilter,
                        onClearSource = { viewModel.setSource(RoomSource.All) },
                        onOpenRoom,
                        onLongClick = { menuFor = it }
                    )
                }
            }
        }
    }

    SearchReveal(searching) {
        SearchOverlay(onClose = { searching = false }, onOpenRoom = onOpenRoom, onOpenMessage = onOpenMessage)
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
            onToggleMuted = then { viewModel.setMuted(room, !room.isMuted) },
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
