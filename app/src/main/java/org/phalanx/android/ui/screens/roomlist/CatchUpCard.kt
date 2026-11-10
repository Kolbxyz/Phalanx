package org.phalanx.android.ui.screens.roomlist

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.ui.theme.Symbols

/**
 * The first thing in the list when there is something to catch up on: how much is waiting and an
 * arrow into it, in the primary container so it reads as the way in rather than as another chat.
 */
@Composable
internal fun CatchUpCard(summary: CatchUpSummary, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(24.dp),
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
        interactionSource = interaction,
        modifier = modifier.fillMaxWidth().pressSink(interaction)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(start = 14.dp, end = 12.dp, top = 14.dp, bottom = 14.dp)
        ) {
            Surface(shape = RoundedCornerShape(16.dp), color = colors.primary, contentColor = colors.onPrimary) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    Icon(Symbols.Summarize, contentDescription = null)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.catch_up_open),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = summaryLine(summary),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(shape = CircleShape, color = colors.primary, contentColor = colors.onPrimary) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Icon(Symbols.KeyboardArrowRight, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun summaryLine(summary: CatchUpSummary): String {
    val unread = summary.unread.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    val mentions = summary.mentions.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    val messages = pluralStringResource(R.plurals.catch_up_card_unread, summary.chats, unread, summary.chats)
    if (mentions == 0) return messages
    return stringResource(
        R.string.catch_up_card_with_mentions,
        messages,
        pluralStringResource(R.plurals.catch_up_card_mentions, mentions, mentions)
    )
}
