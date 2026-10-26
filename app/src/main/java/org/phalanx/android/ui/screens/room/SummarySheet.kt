package org.phalanx.android.ui.screens.room

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.ui.components.ModalSheet

/** The summary as it is written, with how many messages it covers and a reminder it can be wrong. */
@Composable
internal fun SummarySheet(state: SummaryState, onDismiss: () -> Unit) {
    if (state == SummaryState.Idle) return
    ModalSheet(onDismissRequest = onDismiss) {
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.summary_working), style = MaterialTheme.typography.bodyMedium)
                    }
                    if (state.text.isNotEmpty()) Body(state.text)
                }
                is SummaryState.Done -> {
                    Text(
                        stringResource(R.string.summary_scope, state.count),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Body(state.text)
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

@Composable
private fun Body(text: String) {
    SelectionContainer { Text(summaryMarkdown(text), style = MaterialTheme.typography.bodyLarge) }
}
