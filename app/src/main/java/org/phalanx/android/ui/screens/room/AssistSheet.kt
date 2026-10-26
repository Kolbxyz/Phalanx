package org.phalanx.android.ui.screens.room

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.ui.components.ModalSheet

/** A message translated or summed up by the local model, as it is written, then selectable. */
@Composable
internal fun AssistSheet(state: AssistState, onDismiss: () -> Unit) {
    val kind = when (state) {
        AssistState.Idle -> return
        is AssistState.Shown -> state.kind
        is AssistState.Failed -> state.kind
    }
    ModalSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(kind.title), style = MaterialTheme.typography.titleLarge)
            when (state) {
                is AssistState.Shown -> {
                    if (!state.done) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LoadingIndicator(Modifier.size(24.dp))
                            Text(stringResource(kind.working), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (state.text.isNotEmpty()) {
                        SelectionContainer {
                            Text(summaryMarkdown(state.text), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    if (state.done) {
                        if (state.partial) Note(R.string.assist_translation_partial)
                        Note(kind.disclaimer)
                    }
                }
                is AssistState.Failed -> Text(stringResource(state.message), style = MaterialTheme.typography.bodyLarge)
                AssistState.Idle -> Unit
            }
        }
    }
}

@Composable
private fun Note(@StringRes message: Int) {
    Text(
        stringResource(message),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
