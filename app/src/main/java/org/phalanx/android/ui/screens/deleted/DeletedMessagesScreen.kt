package org.phalanx.android.ui.screens.deleted

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.phalanx.android.R
import org.phalanx.android.data.model.ArchivedKind
import org.phalanx.android.data.model.DeletedMessage
import org.phalanx.android.ui.components.BackButton
import org.phalanx.android.ui.components.SegmentGap
import org.phalanx.android.ui.components.SegmentedRow
import org.phalanx.android.ui.components.segmentedShape
import org.phalanx.android.ui.theme.Symbols
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun DeletedMessagesScreen(onBack: () -> Unit, viewModel: DeletedMessagesViewModel = hiltViewModel()) {
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    var confirming by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings_deleted_messages)) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    if (deleted.isNotEmpty()) {
                        IconButton(onClick = { confirming = true }) {
                            Icon(Symbols.Delete, contentDescription = stringResource(R.string.deleted_clear))
                        }
                    }
                },
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
            item(key = "scope") {
                Text(
                    text = stringResource(if (deleted.isEmpty()) R.string.deleted_empty else R.string.deleted_scope),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                )
            }
            itemsIndexed(deleted, key = { _, message -> message.eventId }) { index, message ->
                SegmentedRow(
                    headline = headlineOf(message),
                    supporting = detailsOf(message),
                    shape = segmentedShape(index, deleted.size)
                )
            }
        }
    }
    if (confirming) {
        ClearDialog(
            onConfirm = {
                viewModel.clear()
                confirming = false
            },
            onDismiss = { confirming = false }
        )
    }
}

@Composable
private fun ClearDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Symbols.Delete, contentDescription = null) },
        title = { Text(stringResource(R.string.deleted_clear_title)) },
        text = { Text(stringResource(R.string.deleted_clear_body)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.deleted_clear)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/** The text itself, or for media what kind it was, followed by its caption or file name. */
@Composable
private fun headlineOf(message: DeletedMessage): String {
    if (message.kind == ArchivedKind.Text) return message.text
    val label = stringResource(kindLabel(message.kind))
    return if (message.text.isEmpty()) label else "$label · ${message.text}"
}

@Composable
private fun detailsOf(message: DeletedMessage): String {
    val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    fun format(millis: Long) = formatter.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
    val times = stringResource(
        if (message.wasEdited) R.string.deleted_times_edited else R.string.deleted_times,
        format(message.sentAtMillis),
        format(message.deletedAtMillis)
    )
    return "${message.roomName} · ${message.senderName}\n$times"
}

@StringRes
private fun kindLabel(kind: ArchivedKind): Int = when (kind) {
    ArchivedKind.Text, ArchivedKind.Other -> R.string.timeline_kind_other
    ArchivedKind.Image -> R.string.timeline_kind_image
    ArchivedKind.Video -> R.string.timeline_kind_video
    ArchivedKind.Audio -> R.string.timeline_kind_audio
    ArchivedKind.File -> R.string.timeline_kind_file
    ArchivedKind.Location -> R.string.timeline_kind_location
    ArchivedKind.Sticker -> R.string.timeline_kind_sticker
}
