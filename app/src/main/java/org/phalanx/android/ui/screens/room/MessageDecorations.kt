package org.phalanx.android.ui.screens.room

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.data.model.ReactionSummary
import org.phalanx.android.data.model.ReplyPreview

private val AccentShape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)

/** The quoted message at the top of a reply, or of the composer while replying. */
@Composable
internal fun ReplyQuote(reply: ReplyPreview, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(IntrinsicSize.Min)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
    ) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary, AccentShape)
        )
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            reply.senderName?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }
            Text(
                text = reply.body?.let { summaryOf(it) } ?: stringResource(R.string.timeline_reply_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Tapping a reaction adds or removes this account's own. */
@Composable
internal fun ReactionRow(reactions: List<ReactionSummary>, onToggle: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        reactions.forEach { reaction ->
            val description = stringResource(R.string.timeline_reaction_toggle, reaction.key, reaction.count)
            Surface(
                onClick = { onToggle(reaction.key) },
                shape = CircleShape,
                color = if (reaction.byMe) colors.secondaryContainer else colors.surfaceContainerHigh,
                border = if (reaction.byMe) BorderStroke(1.dp, colors.secondary) else null,
                modifier = Modifier.semantics { contentDescription = description }
            ) {
                Text(
                    text = if (reaction.count > 1) "${reaction.key} ${reaction.count}" else reaction.key,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}
