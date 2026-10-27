package org.phalanx.android.ui.screens.details

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.phalanx.android.R
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.ui.components.BackButton
import org.phalanx.android.ui.components.OpenFileEffect
import org.phalanx.android.ui.components.SegmentedRow
import org.phalanx.android.ui.components.SegmentedSection
import org.phalanx.android.ui.components.segmentedShape
import org.phalanx.android.ui.screens.room.BlockUserDialog
import org.phalanx.android.ui.screens.room.ProtectedRoomScreen
import org.phalanx.android.ui.screens.roomlist.LeaveRoomDialog
import org.phalanx.android.ui.theme.Symbols

/** Everything about one conversation: who and what it is, pinned messages, shared media and files. */
@Composable
fun RoomDetailsScreen(
    onBack: () -> Unit,
    onLeft: () -> Unit,
    onJumpTo: (roomId: String, eventId: String) -> Unit,
    onOpenMedia: (roomId: String, entryId: String) -> Unit,
    onShowAll: (roomId: String, files: Boolean) -> Unit,
    onOpenRoom: (String) -> Unit,
    viewModel: RoomDetailsViewModel = hiltViewModel()
) {
    val room by viewModel.room.collectAsStateWithLifecycle()
    val trust by viewModel.trust.collectAsStateWithLifecycle()
    val pinned by viewModel.pinned.collectAsStateWithLifecycle()
    val media by viewModel.mediaItems.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val openRequest by viewModel.openRequest.collectAsStateWithLifecycle()
    val left by viewModel.left.collectAsStateWithLifecycle()
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    ProtectedRoomScreen()
    var leaving by remember { mutableStateOf(false) }
    var blocking by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(left) { if (left) onLeft() }
    val noticeText = notice?.let { stringResource(it) }
    LaunchedEffect(noticeText) {
        if (noticeText != null) {
            snackbar.showSnackbar(noticeText)
            viewModel.dismissNotice()
        }
    }
    OpenFileEffect(openRequest, viewModel::onOpened) { viewModel.notify(R.string.timeline_open_failed) }

    val open = { message: TimelineEntry.Message ->
        when (val body = message.body) {
            is MessageBody.Image, is MessageBody.Video -> onOpenMedia(viewModel.roomId, message.id)
            is MessageBody.File -> viewModel.openFile(body.media, body.filename, body.mimeType)
            is MessageBody.Audio -> viewModel.openFile(body.media, body.filename, body.mimeType)
            else -> Unit
        }
    }
    val hasMore by viewModel.hasMoreMedia.collectAsStateWithLifecycle()
    var showAllMembers by rememberSaveable { mutableStateOf(false) }
    val members by viewModel.members.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = {}, navigationIcon = { BackButton(onBack) }) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        val current = room ?: return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp
            )
        ) {
            item {
                DetailsHeader(
                    current,
                    trust,
                    onFavourite = viewModel::setFavourite,
                    onUnread = viewModel::setUnread,
                    onMuted = viewModel::setMuted
                )
            }
            if (current.dmUserId != null) item { AlsoOnSection(onOpenRoom) }
            pinnedSection(pinned) { eventId -> onJumpTo(viewModel.roomId, eventId) }
            membersSection(members, showAllMembers) { showAllMembers = true }
            mediaSections(media, hasMore, onShowAll = { files -> onShowAll(viewModel.roomId, files) }, onOpen = open)
            item { DetailsPrivacySection(prefs, viewModel) }
            item {
                SegmentedSection(stringResource(R.string.details_manage)) {
                    val dmUser = current.dmUserId
                    val count = if (dmUser != null) 2 else 1
                    if (dmUser != null) {
                        DangerRow(R.string.action_block, Symbols.Block, segmentedShape(0, count)) {
                            blocking = true
                        }
                    }
                    val leaveShape = segmentedShape(count - 1, count)
                    DangerRow(R.string.room_action_leave, Symbols.Logout, leaveShape) {
                        leaving = true
                    }
                }
            }
        }
        if (leaving) {
            LeaveRoomDialog(current.name, onConfirm = {
                leaving = false
                viewModel.leave()
            }, onDismiss = { leaving = false })
        }
        val dmUser = current.dmUserId
        if (blocking && dmUser != null) {
            BlockUserDialog(current.name, onConfirm = {
                blocking = false
                viewModel.block(dmUser)
            }, onDismiss = { blocking = false })
        }
    }
}

private fun LazyListScope.pinnedSection(pinned: List<TimelineEntry.Message>?, onOpen: (eventId: String) -> Unit) {
    item { DetailsSectionTitle(R.string.details_pinned) }
    if (pinned.isNullOrEmpty()) {
        item { EmptyNote(stringResource(if (pinned == null) R.string.loading else R.string.details_pinned_none)) }
        return
    }
    itemsIndexed(pinned, key = { _, message -> "pin-${message.id}" }) { index, message ->
        // Pinned events always come from the server, so they always have an event ID.
        PinnedRow(message, segmentedShape(index, pinned.size)) { message.eventId?.let(onOpen) }
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
internal fun DetailsSectionTitle(@StringRes title: Int) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
private fun DangerRow(
    @StringRes label: Int,
    icon: ImageVector,
    shape: Shape,
    onClick: () -> Unit
) {
    val error = MaterialTheme.colorScheme.error
    SegmentedRow(
        headline = stringResource(label),
        headlineColor = error,
        shape = shape,
        interaction = Modifier.clickable(onClick = onClick),
        leading = { Icon(icon, contentDescription = null, tint = error) }
    )
}
