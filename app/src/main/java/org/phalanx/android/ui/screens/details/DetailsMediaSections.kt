package org.phalanx.android.ui.screens.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LoadingIndicator
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

internal const val MEDIA_END = "media-end"
private const val COLUMNS = 3

/** How many of each kind show before "Show all": three rows of the grid. */
internal const val SECTION_CAP = 9

/** The room's media split for the two sections, newest first. */
internal class SharedMedia(val visual: List<TimelineEntry.Message>, val files: List<TimelineEntry.Message>)

internal fun sharedMediaOf(media: List<TimelineEntry.Message>?) = SharedMedia(
    visual = media.orEmpty().filter { it.body is MessageBody.Image || it.body is MessageBody.Video },
    files = media.orEmpty().filter { it.body is MessageBody.File || it.body is MessageBody.Audio }
)

/** [media] null while the gallery opens; each section is capped at [SECTION_CAP] until expanded. */
@Suppress("LongParameterList")
internal fun LazyListScope.mediaSections(
    media: List<TimelineEntry.Message>?,
    hasMore: Boolean,
    showAllMedia: Boolean,
    showAllFiles: Boolean,
    onShowAllMedia: () -> Unit,
    onShowAllFiles: () -> Unit,
    onOpen: (TimelineEntry.Message) -> Unit
) {
    val shared = sharedMediaOf(media)
    item { DetailsSectionTitle(R.string.details_media) }
    when {
        media == null -> item(key = "media-placeholder") { GridPlaceholder() }
        shared.visual.isEmpty() && !hasMore -> item { EmptyNote(stringResource(R.string.details_media_none)) }
    }
    val visual = if (showAllMedia) shared.visual else shared.visual.take(SECTION_CAP)
    itemsIndexed(visual.chunked(COLUMNS), key = { _, row -> "row-${row.first().id}" }) { _, row ->
        MediaGridRow(row, COLUMNS, onOpen)
        Spacer(Modifier.height(4.dp))
    }
    if (!showAllMedia && (shared.visual.size > SECTION_CAP || (hasMore && shared.visual.size == SECTION_CAP))) {
        item(key = "media-all") { ShowAllButton(onShowAllMedia) }
    }

    item { DetailsSectionTitle(R.string.details_files) }
    when {
        media == null -> item(key = "files-placeholder") { RowsPlaceholder() }
        shared.files.isEmpty() && !hasMore -> item { EmptyNote(stringResource(R.string.details_files_none)) }
    }
    val files = if (showAllFiles) shared.files else shared.files.take(SECTION_CAP)
    itemsIndexed(files, key = { _, message -> "file-${message.id}" }) { index, message ->
        FileRow(message, segmentedShape(index, files.size)) { onOpen(message) }
        Spacer(Modifier.height(2.dp))
    }
    if (!showAllFiles && (shared.files.size > SECTION_CAP || (hasMore && shared.files.size == SECTION_CAP))) {
        item(key = "files-all") { ShowAllButton(onShowAllFiles) }
    }
    // Reaching this asks for older media while a section still wants more; the gallery pages
    // backwards through the room, so the indicator says it is still looking.
    item(key = MEDIA_END) {
        if (media != null && hasMore) {
            Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) { LoadingIndicator(Modifier.size(40.dp)) }
        } else {
            Spacer(Modifier.height(1.dp))
        }
    }
}

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
