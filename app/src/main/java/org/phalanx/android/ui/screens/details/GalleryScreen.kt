package org.phalanx.android.ui.screens.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.phalanx.android.R
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.ui.components.BackButton
import org.phalanx.android.ui.components.OpenFileEffect
import org.phalanx.android.ui.components.segmentedShape

private const val COLUMNS = 3

// Pages ahead of the reader: this many items from the end, the next one is asked for.
private const val LOAD_AHEAD = 6

/** A room's photos and videos as a grid, or its files as a list, paging back as it scrolls. */
@Composable
fun GalleryScreen(
    onBack: () -> Unit,
    onOpenMedia: (roomId: String, entryId: String) -> Unit,
    viewModel: GalleryViewModel = hiltViewModel()
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMore.collectAsStateWithLifecycle()
    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val paging by viewModel.paging.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val openRequest by viewModel.openRequest.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    NoticeEffect(notice, snackbar, viewModel::dismissNotice)
    OpenFileEffect(openRequest, viewModel::onOpened) { viewModel.notify(R.string.timeline_open_failed) }

    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            if (viewModel.files) {
                listState.layoutInfo.let { nearEnd(it.visibleItemsInfo.lastOrNull()?.index, it.totalItemsCount) }
            } else {
                gridState.layoutInfo.let { nearEnd(it.visibleItemsInfo.lastOrNull()?.index, it.totalItemsCount) }
            }
        }
    }
    // One page at a time, and only while the end is in view; keyed on the pages asked for, so a page
    // that brought nothing of this kind asks for the next.
    LaunchedEffect(nearEnd, pages, hasMore, items != null) {
        if (nearEnd && hasMore && items != null) viewModel.loadMore()
    }

    val title = if (viewModel.files) R.string.details_files else R.string.details_media
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(title)) }, navigationIcon = { BackButton(onBack) }) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        val contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding() + 24.dp
        )
        val loading = isLoading(items, paging, hasMore)
        if (viewModel.files) {
            FilesList(items.orEmpty(), loading, listState, contentPadding) { message ->
                when (val body = message.body) {
                    is MessageBody.File -> viewModel.openFile(body.media, body.filename, body.mimeType)
                    is MessageBody.Audio -> viewModel.openFile(body.media, body.filename, body.mimeType)
                    else -> Unit
                }
            }
        } else {
            MediaGrid(items.orEmpty(), loading, gridState, contentPadding) { onOpenMedia(viewModel.roomId, it.id) }
        }
    }
}

@Composable
private fun FilesList(
    files: List<TimelineEntry.Message>,
    loading: Boolean,
    state: LazyListState,
    contentPadding: PaddingValues,
    onOpen: (TimelineEntry.Message) -> Unit
) {
    LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
        itemsIndexed(files, key = { _, message -> message.id }) { index, message ->
            FileRow(message, segmentedShape(index, files.size)) { onOpen(message) }
        }
        item(key = END_KEY) { GalleryEnd(loading, files.isEmpty(), R.string.details_files_none) }
    }
}

@Composable
private fun MediaGrid(
    media: List<TimelineEntry.Message>,
    loading: Boolean,
    state: LazyGridState,
    contentPadding: PaddingValues,
    onOpen: (TimelineEntry.Message) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(COLUMNS),
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(media, key = { it.id }) { message -> MediaTile(message, Modifier, onOpen) }
        item(key = END_KEY, span = { GridItemSpan(maxLineSpan) }) {
            GalleryEnd(loading, media.isEmpty(), R.string.details_media_none)
        }
    }
}

@Composable
private fun NoticeEffect(notice: Int?, snackbar: SnackbarHostState, onShown: () -> Unit) {
    val text = notice?.let { stringResource(it) }
    LaunchedEffect(text) {
        if (text != null) {
            snackbar.showSnackbar(text)
            onShown()
        }
    }
}

/** Like the timeline's images, loaded items just appear: the indicator only fills an empty screen. */
private fun isLoading(items: List<TimelineEntry.Message>?, paging: Boolean, hasMore: Boolean) =
    items == null || (items.isEmpty() && (paging || hasMore))

private fun nearEnd(lastVisible: Int?, total: Int) = (lastVisible ?: 0) >= total - LOAD_AHEAD

/** A loading indicator while nothing has arrived yet, or what an empty gallery says. */
@Composable
private fun GalleryEnd(loading: Boolean, empty: Boolean, none: Int) {
    Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
        when {
            loading -> LoadingIndicator(Modifier.size(40.dp))
            empty -> EmptyNote(stringResource(none))
        }
    }
}

private const val END_KEY = "end"
