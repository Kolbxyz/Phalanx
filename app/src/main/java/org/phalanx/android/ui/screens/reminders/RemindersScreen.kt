package org.phalanx.android.ui.screens.reminders

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
import org.phalanx.android.data.model.Reminder
import org.phalanx.android.ui.components.BackButton
import org.phalanx.android.ui.components.SegmentGap
import org.phalanx.android.ui.components.SegmentedRow
import org.phalanx.android.ui.components.segmentedShape
import org.phalanx.android.ui.theme.Symbols

/** Every reminder still to come, soonest first: one opens its message, or is cancelled. */
@Composable
fun RemindersScreen(
    onBack: () -> Unit,
    onOpenMessage: (roomId: String, eventId: String) -> Unit,
    viewModel: RemindersViewModel = hiltViewModel()
) {
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.reminders_title)) },
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
            if (reminders.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.reminders_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
            itemsIndexed(reminders, key = { _, reminder -> reminder.id }) { index, reminder ->
                SegmentedRow(
                    headline = reminder.preview,
                    supporting = detailsOf(reminder),
                    shape = segmentedShape(index, reminders.size),
                    leading = { Icon(Symbols.AlarmOn, contentDescription = null) },
                    trailing = {
                        IconButton(onClick = { viewModel.cancel(reminder.id) }) {
                            Icon(Symbols.Close, contentDescription = stringResource(R.string.reminders_cancel))
                        }
                    },
                    interaction = Modifier.clickable(role = Role.Button) {
                        onOpenMessage(reminder.roomId, reminder.eventId)
                    }
                )
            }
        }
    }
}

private fun detailsOf(reminder: Reminder): String {
    val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    val at = formatter.format(Instant.ofEpochMilli(reminder.remindAtMillis).atZone(ZoneId.systemDefault()))
    return "${reminder.roomName} · ${reminder.senderName}\n$at"
}
