package org.phalanx.android.ui.screens.room

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.ui.theme.Symbols

// Must match the recorder's sampling interval: one level per tenth of a second.
private const val MILLIS_PER_SAMPLE = 100L
private const val VISIBLE_BARS = 48

/** Replaces the text field while a voice message is recorded: discard, live waveform, send. */
@Composable
internal fun RecordingRow(levels: List<Float>, onCancel: () -> Unit, onSend: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val recording = stringResource(R.string.composer_recording)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onCancel) {
            Icon(
                Symbols.Delete,
                contentDescription = stringResource(R.string.composer_record_cancel),
                tint = colors.error
            )
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .height(56.dp)
                .background(colors.surfaceContainerHighest, RoundedCornerShape(28.dp))
                .padding(horizontal = 16.dp)
                .semantics { contentDescription = recording },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(10.dp).background(colors.error, CircleShape))
            Text(formatDuration(levels.size * MILLIS_PER_SAMPLE), style = MaterialTheme.typography.labelLarge)
            // The newest samples on the right; older ones scroll off the left as the message grows.
            Waveform(
                samples = levels.takeLast(VISIBLE_BARS).ifEmpty { listOf(0f) },
                progress = 1f,
                modifier = Modifier.weight(1f).height(32.dp)
            )
        }
        FilledIconButton(onClick = onSend, modifier = Modifier.size(48.dp)) {
            Icon(Symbols.Send, contentDescription = stringResource(R.string.composer_record_send))
        }
    }
}
