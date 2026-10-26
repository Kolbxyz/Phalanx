package org.phalanx.android.ui.screens.media

import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.phalanx.android.R
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.ui.components.OpenFileEffect
import org.phalanx.android.ui.components.TopNoticeHost
import org.phalanx.android.ui.screens.room.ForwardSheet
import org.phalanx.android.ui.screens.room.ProtectedRoomScreen
import org.phalanx.android.ui.theme.Symbols

private const val CHROME_ALPHA = 0.72f

/** Swipes through the room's photos and videos, oldest to the left, starting on the one tapped. */
@Composable
fun MediaViewerScreen(
    onClose: () -> Unit,
    onJumpTo: (roomId: String, eventId: String) -> Unit,
    viewModel: MediaViewerViewModel = hiltViewModel()
) {
    ProtectedRoomScreen()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val share by viewModel.shareRequest.collectAsStateWithLifecycle()
    ShareEffect(share, viewModel::onShared)
    val open by viewModel.openRequest.collectAsStateWithLifecycle()
    OpenFileEffect(open, viewModel::onOpened, chooser = true, onNoApp = viewModel::onNoApp)

    // The pages draw their own ground, so swiping them away can fade it.
    val ground = MaterialTheme.colorScheme.surfaceContainerLowest
    Surface(
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxSize()
    ) {
        val list = items
        val state = Modifier.fillMaxSize().background(ground)
        when {
            list == null -> Box(state, Alignment.Center) { LoadingIndicator(Modifier.size(64.dp)) }
            list.isEmpty() -> Box(state, Alignment.Center) {
                Text(stringResource(R.string.timeline_open_failed), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> Pager(list, videos, ground, viewModel, onClose, onJumpTo)
        }
        TopNoticeHost(notice, onDismiss = viewModel::dismissNotice)
    }
}

@Composable
private fun Pager(
    items: List<TimelineEntry.Message>,
    videos: Map<String, VideoFile>,
    ground: Color,
    viewModel: MediaViewerViewModel,
    onClose: () -> Unit,
    onJumpTo: (roomId: String, eventId: String) -> Unit
) {
    // Follows the item, not the index: older pages loading in shift every index along.
    var currentId by rememberSaveable { mutableStateOf(viewModel.entryId) }
    val pager = rememberPagerState(initialPage = items.indexOfFirst { it.id == currentId }.coerceAtLeast(0)) {
        items.size
    }
    LaunchedEffect(items) {
        val index = items.indexOfFirst { it.id == currentId }
        if (index >= 0 && index != pager.currentPage) pager.scrollToPage(index)
    }
    LaunchedEffect(pager, items) {
        snapshotFlow { pager.settledPage }.collect { page ->
            val entry = items.getOrNull(page) ?: return@collect
            currentId = entry.id
            viewModel.loadVideo(entry)
            if (page == 0) viewModel.loadOlder()
        }
    }
    var zoomed by remember { mutableStateOf(false) }
    var chrome by rememberSaveable { mutableStateOf(true) }
    SwipeToDismiss(enabled = !zoomed, background = ground, onDismiss = onClose) {
        HorizontalPager(
            state = pager,
            key = { items[it].id },
            userScrollEnabled = !zoomed,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val entry = items[page]
            MediaPage(
                entry = entry,
                video = videos[entry.id],
                active = page == pager.settledPage,
                onTap = { chrome = !chrome },
                onZoomed = { if (page == pager.settledPage) zoomed = it }
            )
        }
    }
    Box(Modifier.fillMaxSize()) {
        val fade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        AnimatedVisibility(visible = chrome, enter = fadeIn(fade), exit = fadeOut(fade)) {
            items.getOrNull(pager.settledPage)?.let { entry ->
                val eventId = entry.eventId
                ViewerTopBar(
                    entry = entry,
                    viewModel = viewModel,
                    onClose = onClose,
                    onJumpTo = eventId?.let { { onJumpTo(viewModel.roomId, it) } }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewerTopBar(
    entry: TimelineEntry.Message,
    viewModel: MediaViewerViewModel,
    onClose: () -> Unit,
    onJumpTo: (() -> Unit)?
) {
    val context = LocalContext.current
    var info by rememberSaveable { mutableStateOf(false) }
    var forwarding by rememberSaveable { mutableStateOf(false) }
    val describer: PhotoDescribeViewModel = hiltViewModel()
    val canDescribe by describer.ready.collectAsStateWithLifecycle()
    val described by describer.state.collectAsStateWithLifecycle()
    described?.let { DescribeSheet(it, describer::dismiss) }
    if (info) {
        MediaInfoSheet(
            entry = entry,
            onDismiss = { info = false },
            onOpenWith = {
                info = false
                viewModel.openWith(entry)
            },
            onForward = entry.eventId?.let {
                {
                    info = false
                    forwarding = true
                }
            },
            onJumpTo = onJumpTo,
            onDescribe = (entry.body as? MessageBody.Image)?.takeIf { canDescribe }?.let { image ->
                {
                    info = false
                    describer.describe(image)
                }
            }
        )
    }
    if (forwarding) {
        val rooms by viewModel.rooms.collectAsStateWithLifecycle()
        ForwardSheet(
            rooms = rooms?.filter { it.id != viewModel.roomId },
            onPick = {
                forwarding = false
                viewModel.forward(entry, it)
            },
            onDismiss = { forwarding = false }
        )
    }
    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        if (uri != null) viewModel.saveTo(entry, uri)
    }
    TopAppBar(
        title = {
            Column {
                Text(entry.senderName, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    text = DateUtils.formatDateTime(
                        context,
                        entry.timestampMillis,
                        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Symbols.Close, contentDescription = stringResource(R.string.media_viewer_close))
            }
        },
        actions = {
            IconButton(onClick = { viewModel.share(entry) }) {
                Icon(Symbols.Share, contentDescription = stringResource(R.string.media_share))
            }
            IconButton(
                onClick = {
                    if (viewModel.savesThroughPicker) {
                        entry.body.fileOf()?.let { saveAs.launch(it.filename) }
                    } else {
                        viewModel.saveToGallery(entry)
                    }
                }
            ) {
                Icon(Symbols.Download, contentDescription = stringResource(R.string.media_save))
            }
            IconButton(onClick = { info = true }) {
                Icon(Symbols.Info, contentDescription = stringResource(R.string.media_info))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = CHROME_ALPHA)
        )
    )
}
