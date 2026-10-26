package org.phalanx.android.ui.screens.room

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.core.ai.SummaryPoint
import org.phalanx.android.core.ai.SummaryScope
import org.phalanx.android.ui.components.ModalSheet
import org.phalanx.android.ui.theme.Symbols

/**
 * The summary as it is written, then as points that each lead to the message they cite, with
 * how many messages it covers, replies the model can suggest, and a reminder it can be wrong.
 */
@Composable
internal fun SummarySheet(state: SummaryState, actions: SummaryActions) {
    if (state == SummaryState.Idle) return
    ModalSheet(onDismissRequest = actions.onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.summary_title), style = MaterialTheme.typography.titleLarge)
            when (state) {
                is SummaryState.Running -> {
                    Working(R.string.summary_working)
                    if (state.text.isNotEmpty()) {
                        SelectionContainer {
                            Text(summaryMarkdown(state.text), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                is SummaryState.Done -> {
                    Text(
                        stringResource(scopeText(state.scope), state.count),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    state.savedAtMillis?.let { saved ->
                        Text(
                            stringResource(R.string.summary_saved, clockTime(saved)),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        state.points.forEach { Point(it, actions.onJump) }
                    }
                    RepliesPart(state.replies, actions)
                    Text(
                        stringResource(R.string.summary_disclaimer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is SummaryState.Failed ->
                    Text(stringResource(state.message), style = MaterialTheme.typography.bodyLarge)
                SummaryState.Idle -> Unit
            }
        }
    }
}

@StringRes
private fun scopeText(scope: SummaryScope): Int = when (scope) {
    SummaryScope.Latest -> R.string.summary_scope
    SummaryScope.Unread -> R.string.summary_scope_unread
    SummaryScope.SinceLast -> R.string.summary_scope_since_last
}

/** A point on a tonal row; one that cites a message is a button to it. */
@Composable
private fun Point(point: SummaryPoint, onJump: (String) -> Unit) {
    val eventId = point.eventId
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                summaryMarkdown(point.text),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            if (eventId != null) {
                Icon(Symbols.KeyboardArrowRight, contentDescription = stringResource(R.string.summary_point_jump))
            }
        }
    }
    val shape = MaterialTheme.shapes.large
    val color = MaterialTheme.colorScheme.surfaceContainerHigh
    if (eventId == null) {
        Surface(shape = shape, color = color, modifier = Modifier.fillMaxWidth(), content = content)
    } else {
        Surface(
            onClick = { onJump(eventId) },
            shape = shape,
            color = color,
            modifier = Modifier.fillMaxWidth(),
            content = content
        )
    }
}

@Composable
private fun RepliesPart(replies: Replies, actions: SummaryActions) {
    when (replies) {
        Replies.Offered -> FilledTonalButton(onClick = actions.onSuggestReplies) {
            Text(stringResource(R.string.summary_suggest_replies))
        }
        Replies.Writing -> Working(R.string.summary_writing_replies)
        is Replies.Ready -> {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                replies.replies.forEach { reply ->
                    SuggestionChip(
                        onClick = { actions.onUseReply(reply) },
                        label = { Text(reply) },
                        icon = {
                            Icon(
                                Symbols.Edit,
                                contentDescription = stringResource(R.string.summary_use_reply),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }
        }
        is Replies.Failed -> Text(stringResource(replies.message), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Working(@StringRes message: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        LoadingIndicator(Modifier.size(24.dp))
        Text(stringResource(message), style = MaterialTheme.typography.bodyMedium)
    }
}
