package org.phalanx.android.ui.screens.bookmarks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.phalanx.android.R
import org.phalanx.android.data.model.Bookmark
import org.phalanx.android.ui.components.BackButton
import org.phalanx.android.ui.components.SegmentGap
import org.phalanx.android.ui.components.SegmentedRow
import org.phalanx.android.ui.components.segmentedShape
import org.phalanx.android.ui.theme.Symbols

/** Every saved message, newest first: one opens in its room, or is removed. */
@Composable
fun BookmarksScreen(
    onBack: () -> Unit,
    onOpenMessage: (roomId: String, eventId: String) -> Unit,
    viewModel: BookmarksViewModel = hiltViewModel()
) {
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val failed by viewModel.failed.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.bookmarks_title)) },
                navigationIcon = { BackButton(onBack) },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(SegmentGap)
        ) {
            if (failed) {
                item(key = "failed") {
                    Text(
                        text = stringResource(R.string.bookmarks_sync_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
            if (bookmarks.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.bookmarks_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
            itemsIndexed(bookmarks, key = { _, bookmark -> bookmark.eventId }) { index, bookmark ->
                SegmentedRow(
                    headline = bookmark.preview.ifBlank { stringResource(R.string.bookmarks_other_device) },
                    supporting = detailsOf(bookmark),
                    shape = segmentedShape(index, bookmarks.size),
                    leading = { Icon(Symbols.BookmarkFilled, contentDescription = null) },
                    trailing = {
                        IconButton(onClick = { viewModel.remove(bookmark.eventId) }) {
                            Icon(Symbols.Close, contentDescription = stringResource(R.string.bookmarks_remove))
                        }
                    },
                    interaction = Modifier.clickable(role = Role.Button) {
                        onOpenMessage(bookmark.roomId, bookmark.eventId)
                    }
                )
            }
        }
    }
}

private fun detailsOf(bookmark: Bookmark): String {
    val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    val at = formatter.format(Instant.ofEpochMilli(bookmark.savedAtMillis).atZone(ZoneId.systemDefault()))
    val who = listOf(bookmark.roomName, bookmark.senderName).filter { it.isNotBlank() }.joinToString(" · ")
    return if (who.isEmpty()) at else "$who\n$at"
}
