package org.phalanx.android.ui.screens.roomlist

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.phalanx.android.R
import org.phalanx.android.data.model.RoomSummary
import org.phalanx.android.ui.components.InitialAvatar
import org.phalanx.android.ui.components.RoomListPlaceholder
import org.phalanx.android.ui.components.SegmentGap
import org.phalanx.android.ui.components.segmentedShape

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
                actions = { AccountButton(profile?.label.orEmpty(), avatar, onOpenAccount) },
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
                    if (current.rooms.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                            Text(
                                text = stringResource(R.string.roomlist_empty),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        RoomList(current.rooms, padding, onOpenRoom, onLongClick = { menuFor = it })
                    }
            }
        }
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
    rooms: List<RoomSummary>,
    padding: PaddingValues,
    onOpenRoom: (String) -> Unit,
    onLongClick: (RoomSummary) -> Unit
) {
    val motion = MaterialTheme.motionScheme
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
        itemsIndexed(rooms, key = { _, room -> room.id }, contentType = { _, _ -> ROOM_ROW }) { index, room ->
            RoomRow(
                room,
                shape = segmentedShape(index, rooms.size),
                onClick = { onOpenRoom(room.id) },
                onLongClick = { onLongClick(room) },
                // A room that gets a new message slides to the top instead of jumping there.
                modifier = Modifier.animateItem(
                    fadeInSpec = motion.defaultEffectsSpec(),
                    placementSpec = motion.defaultSpatialSpec(),
                    fadeOutSpec = motion.defaultEffectsSpec()
                )
            )
        }
    }
}

private const val ROOM_ROW = 0
