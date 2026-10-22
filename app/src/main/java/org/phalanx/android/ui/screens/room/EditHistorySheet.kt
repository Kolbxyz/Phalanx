package org.phalanx.android.ui.screens.room

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.phalanx.android.R
import org.phalanx.android.data.model.MessageVersion
import org.phalanx.android.ui.components.ModalSheet
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Every version of an edited message, the original first, each with when it was made. */
@Composable
internal fun EditHistorySheet(eventId: String, onDismiss: () -> Unit) {
    val viewModel: EditHistoryViewModel = hiltViewModel(key = "edit-history-$eventId")
    LaunchedEffect(eventId) { viewModel.load(eventId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    ModalSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.action_edit_history),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        when (val current = state) {
            EditHistoryState.Loading ->
                Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { LoadingIndicator() }
            EditHistoryState.Failed -> Text(
                text = stringResource(R.string.edit_history_failed),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp).navigationBarsPadding()
            )
            is EditHistoryState.Loaded -> VersionList(current.versions)
        }
    }
}

@Composable
private fun VersionList(versions: List<MessageVersion>) {
    val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding()) {
        // Versions have no ID of their own here; their order never changes while the sheet is open.
        itemsIndexed(versions, key = { index, _ -> index }) { index, version ->
            val time = formatter.format(Instant.ofEpochMilli(version.timestampMillis).atZone(ZoneId.systemDefault()))
            ListItem(
                overlineContent = if (index == 0) {
                    { Text(stringResource(R.string.edit_history_original)) }
                } else {
                    null
                },
                headlineContent = { Text(summaryOf(version.body)) },
                supportingContent = { Text(time) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            )
        }
    }
}
