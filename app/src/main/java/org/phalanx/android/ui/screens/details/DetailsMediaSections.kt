package org.phalanx.android.ui.screens.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.ui.components.segmentedShape

private const val COLUMNS = 3

/** How many photos and videos show here: three rows of the grid. The rest open in the gallery. */
private const val MEDIA_CAP = 9

/** How many files show here, newest first. */
private const val FILES_CAP = 5

/** The room's media split for the two sections, newest first. */
internal class SharedMedia(val visual: List<TimelineEntry.Message>, val files: List<TimelineEntry.Message>)

internal fun sharedMediaOf(media: List<TimelineEntry.Message>?) = SharedMedia(
    visual = media.orEmpty().filter { it.body is MessageBody.Image || it.body is MessageBody.Video },
    files = media.orEmpty().filter { it.body is MessageBody.File || it.body is MessageBody.Audio }
)

/**
 * The newest of what the gallery has loaded, [media] null while it opens. Nothing pages from here:
 * "Show all" opens the gallery screen, which loads older pages as it is scrolled.
 */
internal fun LazyListScope.mediaSections(
    media: List<TimelineEntry.Message>?,
    hasMore: Boolean,
    onShowAll: (files: Boolean) -> Unit,
    onOpen: (TimelineEntry.Message) -> Unit
) {
    val shared = sharedMediaOf(media)
    item { DetailsSectionTitle(R.string.details_media) }
    when {
        media == null -> item(key = "media-placeholder") { GridPlaceholder() }
        shared.visual.isEmpty() -> item { EmptyNote(stringResource(noneNote(R.string.details_media_none, hasMore))) }
    }
    val rows = shared.visual.take(MEDIA_CAP).chunked(COLUMNS)
    itemsIndexed(rows, key = { _, row -> "row-${row.first().id}" }) { _, row ->
        MediaGridRow(row, COLUMNS, onOpen)
        Spacer(Modifier.height(4.dp))
    }
    if (media != null && (shared.visual.size > MEDIA_CAP || hasMore)) {
        item(key = "media-all") { ShowAllButton { onShowAll(false) } }
    }

    item { DetailsSectionTitle(R.string.details_files) }
    when {
        media == null -> item(key = "files-placeholder") { RowsPlaceholder() }
        shared.files.isEmpty() -> item { EmptyNote(stringResource(noneNote(R.string.details_files_none, hasMore))) }
    }
    val files = shared.files.take(FILES_CAP)
    itemsIndexed(files, key = { _, message -> "file-${message.id}" }) { index, message ->
        FileRow(message, segmentedShape(index, files.size)) { onOpen(message) }
        Spacer(Modifier.height(2.dp))
    }
    if (media != null && (shared.files.size > FILES_CAP || hasMore)) {
        item(key = "files-all") { ShowAllButton { onShowAll(true) } }
    }
}

/** With older history still unread, "none" only holds for the recent messages. */
private fun noneNote(none: Int, hasMore: Boolean) = if (hasMore) R.string.details_none_recent else none

@Composable
private fun ShowAllButton(onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = 8.dp), Alignment.Center) {
        FilledTonalButton(onClick = onClick) { Text(stringResource(R.string.details_show_all)) }
    }
}

/** Tiles in the grid's shape, so the section doesn't jump when the thumbnails arrive. */
@Composable
private fun GridPlaceholder() {
    val description = stringResource(R.string.loading)
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(COLUMNS) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.weight(1f).aspectRatio(1f)
            ) {}
        }
    }
}

@Composable
private fun RowsPlaceholder() {
    Surface(
        shape = segmentedShape(0, 1),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().height(64.dp)
    ) {}
}
