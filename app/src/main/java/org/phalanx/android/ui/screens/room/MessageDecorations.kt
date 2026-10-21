package org.phalanx.android.ui.screens.room

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.data.model.ReactionSummary
import org.phalanx.android.data.model.ReplyPreview

private val QuoteShape = RoundedCornerShape(18.dp)
private const val QUOTE_ALPHA = 0.16f
private const val QUOTE_BODY_ALPHA = 0.9f

/** The quoted message at the top of a reply, or of the composer while replying. */
@Composable
internal fun ReplyQuote(
    reply: ReplyPreview,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary
) {
    // Tinted from the accent, so the quote reads on any bubble colour, including this account's.
    // The bar is an inset pill rather than an edge, so it never has to follow the corner's curve.
    Row(
        modifier = modifier
            .clip(QuoteShape)
            .background(accent.copy(alpha = QUOTE_ALPHA))
            .height(IntrinsicSize.Min)
            .padding(8.dp)
    ) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(accent, CircleShape)
        )
        Column(modifier = Modifier.padding(start = 10.dp, end = 4.dp)) {
            reply.senderName?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = when {
                    reply.body != null -> summaryOf(reply.body)
                    reply.isUnavailable -> stringResource(R.string.timeline_reply_unavailable)
                    else -> stringResource(R.string.timeline_reply_loading)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = LocalContentColor.current.copy(alpha = QUOTE_BODY_ALPHA),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Tapping a reaction adds or removes this account's own; holding it shows who reacted. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ReactionRow(
    reactions: List<ReactionSummary>,
    onToggle: (String) -> Unit,
    onShowReactors: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        reactions.forEach { reaction ->
            val description = stringResource(R.string.timeline_reaction_toggle, reaction.key, reaction.count)
            // Ringed in the background colour, so a chip overlapping the bubble's edge stays distinct from it.
            Surface(
                shape = CircleShape,
                color = if (reaction.byMe) colors.primaryContainer else colors.surfaceContainerHighest,
                contentColor = if (reaction.byMe) colors.onPrimaryContainer else colors.onSurface,
                border = BorderStroke(2.dp, colors.background),
                modifier = Modifier
                    .clip(CircleShape)
                    .combinedClickable(
                        onClickLabel = description,
                        onClick = { onToggle(reaction.key) },
                        onLongClickLabel = stringResource(R.string.timeline_reactors_show),
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onShowReactors(reaction.key)
                        }
                    )
                    .semantics { contentDescription = description }
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

/** Draws the content [overlap] higher than laid out and takes that much less room, so it sits over what is above. */
internal fun Modifier.overlapAbove(overlap: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val shift = overlap.roundToPx().coerceAtMost(placeable.height)
    layout(placeable.width, placeable.height - shift) { placeable.place(0, -shift) }
}
