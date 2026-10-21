package org.phalanx.android.ui.screens.room

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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.data.model.ReactionSummary
import org.phalanx.android.data.model.ReplyPreview

private val AccentShape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
private const val QUOTE_ALPHA = 0.12f

/** The quoted message at the top of a reply, or of the composer while replying. */
@Composable
internal fun ReplyQuote(
    reply: ReplyPreview,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary
) {
    // Tinted from the accent, so the quote reads on any bubble colour, including this account's.
    Row(
        modifier = modifier
            .height(IntrinsicSize.Min)
            .background(accent.copy(alpha = QUOTE_ALPHA), RoundedCornerShape(16.dp))
    ) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(accent, AccentShape)
        )
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            reply.senderName?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                    maxLines = 1
                )
            }
            Text(
                text = reply.body?.let { summaryOf(it) } ?: stringResource(R.string.timeline_reply_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalContentColor.current.copy(alpha = 0.8f),
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
                color = if (reaction.byMe) colors.primaryContainer else colors.surfaceContainerHigh,
                contentColor = if (reaction.byMe) colors.onPrimaryContainer else colors.onSurface,
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
