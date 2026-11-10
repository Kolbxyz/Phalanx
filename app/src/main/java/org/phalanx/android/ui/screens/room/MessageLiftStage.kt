package org.phalanx.android.ui.screens.room

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.phalanx.android.R
import org.phalanx.android.core.format.EntityAction
import org.phalanx.android.core.format.FoundEntity
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.ui.components.riseIn
import org.phalanx.android.ui.components.stagger
import org.phalanx.android.ui.theme.Symbols

private val QuickReactions = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")

/**
 * The long-pressed message, lifted above the dimmed timeline on its own side: who sent it and when,
 * the quick reactions over it, the message itself a size up with what was found in it underlined,
 * and what can be done with those finds right under it.
 */
@Composable
internal fun MessageLiftStage(
    message: TimelineEntry.Message,
    pinned: Boolean,
    bookmarked: Boolean,
    found: List<FoundEntity>,
    onReact: (String) -> Unit,
    onMoreReactions: () -> Unit,
    onReactWithMix: (() -> Unit)?,
    onEntityAction: (EntityAction) -> Unit
) {
    val align = if (message.isOwn) Alignment.End else Alignment.Start
    Column(Modifier.fillMaxWidth(), horizontalAlignment = align, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            metaOf(message, pinned, bookmarked),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = META_ALPHA),
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        if (message.body != MessageBody.Redacted && message.eventId != null) {
            ReactionPill(message, onReact, onMoreReactions, onReactWithMix)
        }
        LiftedBubble(message, found)
        SmartChips(found, onEntityAction)
    }
}

@Composable
private fun metaOf(message: TimelineEntry.Message, pinned: Boolean, bookmarked: Boolean): String {
    val parts = buildList {
        add(if (message.isOwn) stringResource(R.string.summary_you) else message.senderName)
        add(remember(message.timestampMillis) { formatTime(message.timestampMillis) })
        if (pinned) add(stringResource(R.string.message_pinned))
        if (bookmarked) add(stringResource(R.string.message_bookmarked))
    }
    return parts.joinToString(" · ")
}

@Composable
private fun ReactionPill(
    message: TimelineEntry.Message,
    onReact: (String) -> Unit,
    onMore: () -> Unit,
    onMix: (() -> Unit)?
) {
    val colors = MaterialTheme.colorScheme
    // The mix button leaves each a little less room on a narrow phone.
    val size = if (onMix != null) 40.dp else 44.dp
    Surface(shape = RoundedCornerShape(28.dp), color = colors.surfaceContainerHigh, shadowElevation = 6.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(4.dp)) {
            QuickReactions.forEachIndexed { index, key ->
                val mine = message.reactions.any { it.key == key && it.byMe }
                Surface(
                    onClick = { onReact(key) },
                    shape = CircleShape,
                    color = if (mine) colors.secondaryContainer else Color.Transparent,
                    modifier = Modifier.size(size).riseIn(stagger(index, REACT_FIRST, REACT_STEP), from = REACT_FROM)
                ) {
                    Box(contentAlignment = Alignment.Center) { Text(key, fontSize = 23.sp) }
                }
            }
            Surface(onClick = onMore, shape = CircleShape, color = Color.Transparent, modifier = Modifier.size(size)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Symbols.Add, contentDescription = stringResource(R.string.reaction_more))
                }
            }
            onMix?.let { onClick ->
                Surface(
                    onClick = onClick,
                    shape = MaterialShapes.Puffy.toShape(),
                    color = colors.tertiaryContainer,
                    contentColor = colors.onTertiaryContainer,
                    modifier = Modifier.size(size)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Symbols.Sticker, contentDescription = stringResource(R.string.kitchen_react))
                    }
                }
            }
        }
    }
}

@Composable
private fun LiftedBubble(message: TimelineEntry.Message, found: List<FoundEntity>) {
    val colors = MaterialTheme.colorScheme
    val own = message.isOwn
    // Tertiary on the user's own primary bubble would hardly show: their underline is the text colour.
    val highlight = if (own) colors.onPrimary else colors.tertiary
    val text = when (val body = message.body) {
        is MessageBody.Text -> remember(body.text, found, highlight) { underlined(body.text, found, highlight) }
        else -> AnnotatedString(summaryOf(body))
    }
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = if (own) colors.primary else colors.surfaceContainerHigh,
        contentColor = if (own) colors.onPrimary else colors.onSurface,
        shadowElevation = 8.dp,
        modifier = Modifier.widthIn(max = 320.dp)
    ) {
        Text(
            text,
            fontSize = 18.sp,
            lineHeight = 26.sp,
            maxLines = MAX_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
        )
    }
}

/** [text] with each thing the phone found in it underlined, where the chips under it act on. */
private fun underlined(text: String, found: List<FoundEntity>, color: Color): AnnotatedString = buildAnnotatedString {
    append(text)
    val style = SpanStyle(color = color, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline)
    found.forEach { entity ->
        val at = text.indexOf(entity.text)
        if (at >= 0) addStyle(style, at, at + entity.text.length)
    }
}

// The meta line sits on the scrim, which is dark in both themes.
private const val META_ALPHA = 0.85f
private const val MAX_LINES = 8
private const val REACT_FIRST = 60L
private const val REACT_STEP = 30L
private const val REACT_FROM = 0.3f
