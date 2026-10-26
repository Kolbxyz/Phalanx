package org.phalanx.android.ui.screens.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
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

/** What the vision model saw in the photo, and the text it read in it, as it is written. */
@Composable
internal fun DescribeSheet(state: DescribeState, onDismiss: () -> Unit) {
    ModalSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.describe_title), style = MaterialTheme.typography.titleLarge)
            when (state) {
                DescribeState.Working -> Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LoadingIndicator(Modifier.size(32.dp))
                    Text(stringResource(R.string.describe_working), style = MaterialTheme.typography.bodyMedium)
                }
                is DescribeState.Done -> SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(state.words.description, style = MaterialTheme.typography.bodyLarge)
                        if (state.words.text.isNotBlank()) {
                            Text(
                                stringResource(R.string.describe_text_found),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(state.words.text, style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(
                            stringResource(R.string.summary_disclaimer),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is DescribeState.Failed -> Text(
                    stringResource(state.message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
