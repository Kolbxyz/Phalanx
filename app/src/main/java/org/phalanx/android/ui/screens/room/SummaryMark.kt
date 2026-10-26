package org.phalanx.android.ui.screens.room

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.phalanx.android.R
import org.phalanx.android.core.ai.SummaryCheckpoint
import org.phalanx.android.ui.theme.Symbols

/**
 * Where a summary stops, shown under the last message it covers, like a date in the timeline:
 * when it was written, and a tap shows it again.
 */
@Composable
internal fun SummaryMark(checkpoint: SummaryCheckpoint, onOpen: (SummaryCheckpoint) -> Unit) {
    val time = remember(checkpoint.madeAtMillis) { clockTime(checkpoint.madeAtMillis) }
    val openLabel = stringResource(R.string.summary_mark_open)
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        AssistChip(
            onClick = { onOpen(checkpoint) },
            label = { Text(stringResource(R.string.summary_mark, time)) },
            leadingIcon = {
                Icon(
                    Symbols.Summarize,
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize)
                )
            },
            modifier = Modifier.semantics {
                onClick(label = openLabel) {
                    onOpen(checkpoint)
                    true
                }
            }
        )
    }
}

/** [millis] as a short time of day, in the phone's format. */
internal fun clockTime(millis: Long): String = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
