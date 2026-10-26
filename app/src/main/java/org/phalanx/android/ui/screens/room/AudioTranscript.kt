package org.phalanx.android.ui.screens.room

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.phalanx.android.R
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.ui.theme.Symbols

private const val MUTED = 0.7f

/**
 * Under a voice message: a button to write it out, then the text as it is written. Shown only
 * once a speech model is imported; the text stays on this phone.
 */
@Composable
internal fun AudioTranscript(key: String, body: MessageBody.Audio) {
    val transcripts = LocalTranscripts.current ?: return
    val available by transcripts.available.collectAsStateWithLifecycle()
    val states by transcripts.states.collectAsStateWithLifecycle()
    LaunchedEffect(key) { transcripts.restore(key) }
    val padding = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
    when (val state = states[key]) {
        null -> if (available) {
            TextButton(onClick = { transcripts.transcribe(key, body) }, modifier = Modifier.padding(start = 4.dp)) {
                Icon(Symbols.SpeechToText, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.transcript_action), modifier = Modifier.padding(start = 6.dp))
            }
        }
        is TranscriptState.Writing -> Muted(state.text.ifEmpty { stringResource(R.string.transcript_writing) }, padding)
        is TranscriptState.Done -> Column(padding, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SelectionContainer {
                Text(
                    state.transcript.text.ifEmpty { stringResource(R.string.transcript_empty) },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (state.transcript.partial) Muted(stringResource(R.string.transcript_partial), Modifier)
        }
        is TranscriptState.Failed -> TextButton(
            onClick = { transcripts.transcribe(key, body) },
            modifier = Modifier.padding(start = 4.dp)
        ) {
            Text(stringResource(state.message), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun Muted(text: String, modifier: Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontStyle = FontStyle.Italic,
        color = LocalContentColor.current.copy(alpha = MUTED),
        modifier = modifier
    )
}
