package org.phalanx.android.ui.screens.roomlist

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import org.phalanx.android.ui.components.LiftOverlay
import org.phalanx.android.ui.components.PhalanxSnackbarHost
import org.phalanx.android.ui.components.RoomListPlaceholder
import org.phalanx.android.ui.components.ScrollHairline
import org.phalanx.android.ui.components.showReport
import org.phalanx.android.ui.components.showUndo
import org.phalanx.android.ui.screens.search.SearchOverlay
import org.phalanx.android.ui.theme.Symbols

@Composable
fun RoomListScreen(
    onOpenRoom: (String) -> Unit,
    onOpenMessage: (roomId: String, eventId: String) -> Unit,
    onOpenDetails: (String) -> Unit,
    onOpenAccount: () -> Unit,
    onOpenSettings: () -> Unit,
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
    val catchUp by viewModel.catchUp.collectAsStateWithLifecycle()
    val reminders by viewModel.reminderCount.collectAsStateWithLifecycle()
    var menuFor by remember { mutableStateOf<RoomSummary?>(null) }
    val peek: PeekViewModel = hiltViewModel()
    val peekLines by peek.lines.collectAsStateWithLifecycle()
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
    RoomListDrawer(
        drawerState = drawer,
        enabled = loaded != null,
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
        },
        onOpenSettings = {
            scope.launch { drawer.close() }
            onOpenSettings()
        }
    ) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                Column {
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
                                Icon(Symbols.Menu, contentDescription = stringResource(R.string.roomlist_menu_open))
                            }
                        },
                        actions = {
                            IconButton(onClick = { searching = true }) {
                                Icon(Symbols.Search, contentDescription = stringResource(R.string.search_open))
                            }
                            AccountButton(profile?.label.orEmpty(), avatar, onOpenAccount)
                        },
                        expandedHeight = 72.dp,
                        // One colour however far the list scrolls; the hairline below marks the edge.
                        colors = TopAppBarDefaults.topAppBarColors(
                            scrolledContainerColor = MaterialTheme.colorScheme.surface
                        ),
                        scrollBehavior = scrollBehavior
                    )
                    if (loaded != null) NetworkTabs(loaded.sources, loaded.source, viewModel::setSource)
                    ScrollHairline(scrollBehavior.state.overlappedFraction > 0f)
                }
            },
            snackbarHost = { PhalanxSnackbarHost(snackbar) }
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
                        catchUp,
                        viewModel::setFilter,
                        onOpenCatchUp,
                        onOpenRoom,
                        onLongClick = {
                            menuFor = it
                            peek.peek(it.id)
                        },
                        liftedId = menuFor?.id
                    )
                }
            }
        }
    }

    SearchReveal(searching) {
        SearchOverlay(onClose = { searching = false }, onOpenRoom = onOpenRoom, onOpenMessage = onOpenMessage)
    }

    // The menu follows the room as its actions land, so a toggle shows its new state at once.
    val menuRoom = menuFor?.let { picked -> loaded?.rooms?.firstOrNull { it.id == picked.id } ?: picked }
    val archived = stringResource(R.string.roomlist_archived, menuRoom?.name.orEmpty())
    val left = stringResource(R.string.roomlist_left, menuRoom?.name.orEmpty())
    val undo = stringResource(R.string.undo)
    LiftOverlay(
        item = menuRoom,
        onDismiss = { menuFor = null },
        closeLabel = stringResource(R.string.menu_close),
        stage = { room -> PeekCard(room, peekLines) },
        sheet = { room ->
            RoomActions(
                room = room,
                onToggleRead = { viewModel.setUnread(room, !room.hasUnread) },
                onToggleFavourite = { viewModel.setFavourite(room, !room.isFavourite) },
                onToggleMuted = { viewModel.setMuted(room, !room.isMuted) },
                onArchive = {
                    menuFor = null
                    val archiving = !room.isLowPriority
                    viewModel.setArchived(room, archiving)
                    if (archiving) scope.launch { snackbar.showUndo(
                        archived,
                        undo
                    ) { viewModel.setArchived(room, false) } }
                },
                onDetails = {
                    menuFor = null
                    onOpenDetails(room.id)
                },
                onLeave = {
                    menuFor = null
                    viewModel.leave(room)
                    scope.launch { snackbar.showReport(left) }
                }
            )
        }
    )
}
