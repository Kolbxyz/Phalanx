package org.phalanx.android.ui.screens.search

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.phalanx.android.R
import org.phalanx.android.data.model.MediaRequest
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.repository.MessageHit
import org.phalanx.android.ui.components.MatrixAvatar
import org.phalanx.android.ui.screens.room.summaryOf
import org.phalanx.android.ui.theme.Symbols

private const val PREVIEW_COUNT = 4
private const val COLUMNS = 3
private const val THUMB_PX = 360

@Suppress("LongParameterList")
@Composable
internal fun SearchResultsList(
    query: String,
    category: SearchCategory,
    results: SearchResults,
    onOpenRoom: (String) -> Unit,
    onOpenFile: (MessageHit) -> Unit,
    onShowCategory: (SearchCategory) -> Unit,
    onLoadMore: () -> Unit
) {
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 3
        }
    }
    val found = results.messages.size + results.media.size + results.files.size
    LaunchedEffect(nearEnd, found, results.endReached) {
        if (nearEnd && !results.endReached && query.isNotBlank()) onLoadMore()
    }
    val all = category == SearchCategory.All
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().imePadding().navigationBarsPadding()) {
        if (query.isBlank()) {
            item { Note(stringResource(R.string.search_intro)) }
            return@LazyColumn
        }
        if (all || category == SearchCategory.Chats) {
            val more = { onShowCategory(SearchCategory.Chats) }
            section(R.string.search_chats, results.chats, all, more, { "c-${it.id}" }) {
                ListItem(
                    headlineContent = { Text(highlight(it.name, query), maxLines = 1) },
                    leadingContent = { MatrixAvatar(it.name, it.avatar, 40.dp) },
                    modifier = Modifier.clickable { onOpenRoom(it.id) }
                )
            }
        }
        if (all || category == SearchCategory.Messages) {
            val more = { onShowCategory(SearchCategory.Messages) }
            section(R.string.search_messages, results.messages, all, more, { "m-${it.eventId}" }) {
                MessageRow(it, results.roomNames[it.roomId], query) { onOpenRoom(it.roomId) }
            }
        }
        if (all || category == SearchCategory.Media) {
            val shown = if (all) results.media.take(COLUMNS) else results.media
            if (shown.isNotEmpty()) {
                item(key = "media-title") {
                    val more = all && results.media.size > COLUMNS
                    Title(R.string.search_media, more) { onShowCategory(SearchCategory.Media) }
                }
                items(shown.chunked(COLUMNS), key = { "media-${it.first().eventId}" }) { row ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        row.forEach { hit -> MediaTile(hit, Modifier.weight(1f)) { onOpenRoom(hit.roomId) } }
                        repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        if (all || category == SearchCategory.Files) {
            val more = { onShowCategory(SearchCategory.Files) }
            section(R.string.search_files, results.files, all, more, { "f-${it.eventId}" }) {
                ListItem(
                    headlineContent = {
                        Text(highlight(summaryOf(it.body), query), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = { Text(origin(it.senderName, results.roomNames[it.roomId]), maxLines = 1) },
                    leadingContent = { Icon(Symbols.InsertDriveFile, contentDescription = null) },
                    modifier = Modifier.clickable { onOpenFile(it) }
                )
            }
        }
        item(key = "footer") {
            when {
                results.loading -> Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) {
                    LoadingIndicator(Modifier.size(40.dp))
                }
                results.chats.isEmpty() && found == 0 -> Note(stringResource(R.string.search_none))
                else -> Note(stringResource(R.string.search_scope))
            }
        }
    }
}

/** Up to [PREVIEW_COUNT] rows under "All", everything in the kind's own tab. */
@Suppress("LongParameterList")
private fun <T> LazyListScope.section(
    title: Int,
    rows: List<T>,
    preview: Boolean,
    onMore: () -> Unit,
    key: (T) -> String,
    row: @Composable (T) -> Unit
) {
    if (rows.isEmpty()) return
    item(key = "title-$title") { Title(title, preview && rows.size > PREVIEW_COUNT, onMore) }
    items(if (preview) rows.take(PREVIEW_COUNT) else rows, key = key) { row(it) }
}

@Composable
private fun Title(title: Int, more: Boolean, onMore: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        if (more) TextButton(onClick = onMore) { Text(stringResource(R.string.details_show_all)) }
    }
}

@Composable
private fun MessageRow(hit: MessageHit, roomName: String?, query: String, onClick: () -> Unit) {
    val context = LocalContext.current
    ListItem(
        overlineContent = {
            Text(origin(hit.senderName, roomName), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        headlineContent = {
            Text(highlight(summaryOf(hit.body), query), maxLines = 3, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = {
            Text(
                DateUtils.getRelativeTimeSpanString(context, hit.timestampMillis).toString(),
                style = MaterialTheme.typography.labelSmall
            )
        },
        leadingContent = { MatrixAvatar(hit.senderName, hit.senderAvatar, 40.dp) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun MediaTile(hit: MessageHit, modifier: Modifier, onClick: () -> Unit) {
    val body = hit.body
    val thumbnail = when (body) {
        is MessageBody.Image -> body.thumbnail ?: body.media
        is MessageBody.Video -> body.thumbnail
        else -> null
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.aspectRatio(1f).clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            thumbnail?.let {
                AsyncImage(
                    model = MediaRequest(it, THUMB_PX),
                    contentDescription = summaryOf(body),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (body is MessageBody.Video) Icon(Symbols.PlayArrow, contentDescription = null)
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(24.dp)
    )
}

/** [text] with every occurrence of [query] in bold, ignoring case. */
@Composable
private fun highlight(text: String, query: String) = buildAnnotatedString {
    val needle = query.trim()
    if (needle.isEmpty()) {
        append(text)
        return@buildAnnotatedString
    }
    val accent = MaterialTheme.colorScheme.primary
    var start = 0
    while (true) {
        val at = text.indexOf(needle, start, ignoreCase = true)
        if (at < 0) break
        append(text.substring(start, at))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = accent)) {
            append(text.substring(at, at + needle.length))
        }
        start = at + needle.length
    }
    append(text.substring(start))
}

/** "Sender · Conversation", or just the sender when the conversation is unknown. */
private fun origin(sender: String, roomName: String?): String = listOfNotNull(sender, roomName).joinToString(" · ")
