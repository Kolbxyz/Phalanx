package org.phalanx.android.ui.screens.room

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.model.PollAnswer
import org.phalanx.android.ui.theme.Symbols

private const val MUTED = 0.7f

/**
 * A poll in the timeline: tap an answer to vote, again to take the vote back. Counts show as bars
 * when the poll discloses them, or once it has ended.
 */
@Composable
internal fun PollContent(
    body: MessageBody.Poll,
    onVote: (List<String>) -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.width(MediaWidth), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Symbols.Poll, contentDescription = stringResource(R.string.timeline_kind_poll))
            Text(body.question, style = MaterialTheme.typography.titleMedium)
        }
        val total = body.totalVotes
        body.answers.forEach { answer ->
            AnswerRow(body, answer, total, onTap = { body.voteAfterTapping(answer.id)?.let(onVote) }, onLongPress)
        }
        Text(
            text = footer(body, total),
            style = MaterialTheme.typography.labelMedium,
            color = LocalContentColor.current.copy(alpha = MUTED)
        )
    }
}

@Composable
private fun AnswerRow(
    poll: MessageBody.Poll,
    answer: PollAnswer,
    total: Int,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val chosen = answer.id in poll.myAnswers
    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickable(enabled = !poll.ended, role = Role.Checkbox, onLongClick = onLongPress, onClick = onTap)
            .padding(vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Null click handlers: the whole row answers the tap, and reads as one control.
            if (poll.maxSelections == 1) {
                RadioButton(selected = chosen, onClick = null, enabled = !poll.ended)
            } else {
                Checkbox(checked = chosen, onCheckedChange = null, enabled = !poll.ended)
            }
            Text(
                text = answer.text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            )
            if (poll.showsResults) Text("${answer.votes}", style = MaterialTheme.typography.labelLarge)
        }
        if (poll.showsResults) {
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else answer.votes.toFloat() / total },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun footer(poll: MessageBody.Poll, total: Int): String {
    val votes = pluralStringResource(R.plurals.poll_votes, total, total)
    return when {
        poll.ended -> stringResource(R.string.poll_ended, votes)
        !poll.disclosed -> stringResource(R.string.poll_hidden_results)
        poll.maxSelections > 1 -> stringResource(R.string.poll_pick_several, poll.maxSelections, votes)
        else -> votes
    }
}
