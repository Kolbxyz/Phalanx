package org.phalanx.android.ui.screens.room

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.phalanx.android.R
import org.phalanx.android.data.model.MediaRequest
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.model.ReactionSummary
import org.phalanx.android.data.model.ReplyPreview

private val QuoteShape = RoundedCornerShape(18.dp)
private const val QUOTE_ALPHA = 0.16f
private const val QUOTE_BODY_ALPHA = 0.9f
private val QuoteThumbnailSize = 40.dp
private val QuoteThumbnailShape = RoundedCornerShape(10.dp)
private const val QUOTE_THUMBNAIL_PX = 160

/** The quoted message at the top of a reply, or of the composer while replying. */
@Composable
internal fun ReplyQuote(
    reply: ReplyPreview,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null
) {
    // Tinted from the accent, so the quote reads on any bubble colour, including this account's.
    // The bar is an inset pill rather than an edge, so it never has to follow the corner's curve.
    Row(
        modifier = modifier
            .clip(QuoteShape)
            .background(accent.copy(alpha = QUOTE_ALPHA))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .height(IntrinsicSize.Min)
            .padding(8.dp)
    ) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(accent, CircleShape)
        )
        Column(modifier = Modifier.weight(1f, fill = false).padding(start = 10.dp, end = 4.dp)) {
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
        reply.body?.let { quotedPicture(it) }?.takeIf { LocalTimelinePrefs.current.autoLoadMedia }?.let {
            AsyncImage(
                model = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .padding(start = 4.dp)
                    .size(QuoteThumbnailSize)
                    .clip(QuoteThumbnailShape)
            )
        }
    }
}

/** A small version of the quoted photo or video, beside its "Photo" or caption. */
private fun quotedPicture(body: MessageBody): MediaRequest? = when (body) {
    is MessageBody.Image -> MediaRequest(body.thumbnail ?: body.media, QUOTE_THUMBNAIL_PX)
    is MessageBody.Video -> videoPreview(body, QUOTE_THUMBNAIL_PX)
    else -> null
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
            val description = stringResource(R.string.timeline_reaction_toggle, reactionName(reaction), reaction.count)
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
                Box(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
                    ReactionKey(
                        reaction = reaction,
                        imageSize = 20.dp,
                        style = MaterialTheme.typography.labelLarge,
                        suffix = reaction.count.takeIf { it > 1 }?.toString()
                    )
                }
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
