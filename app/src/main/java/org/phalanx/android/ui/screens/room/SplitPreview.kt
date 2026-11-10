package org.phalanx.android.ui.screens.room

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import org.phalanx.android.R
import org.phalanx.android.core.format.MessageSplitter
import org.phalanx.android.ui.theme.Symbols

/**
 * How the draft will go out: [parts] as the network will receive them, each at most [budget] long.
 * The meter shows once the draft splits, or is close to; it opens the preview of where it splits.
 */
@Composable
internal fun SplitMeter(parts: List<MessageSplitter.Part>, budget: Int, length: Int, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val split = parts.size > 1
    val count = NumberFormat.getIntegerInstance()
    val label = if (split) {
        pluralStringResource(R.plurals.split_meter_parts, parts.size, parts.size)
    } else {
        stringResource(R.string.split_meter_near, count.format(length), count.format(budget))
    }
    val description = if (split) {
        pluralStringResource(R.plurals.split_meter_parts_description, parts.size, parts.size)
    } else {
        stringResource(R.string.split_meter_near_description, count.format(length), count.format(budget))
    }
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(16.dp),
        color = if (split) colors.tertiaryContainer else colors.surfaceContainerHigh,
        contentColor = if (split) colors.onTertiaryContainer else colors.onSurface,
        modifier = Modifier
            .heightIn(min = 32.dp)
            .semantics(mergeDescendants = true) { contentDescription = description }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            if (split) ShareBars(parts, budget)
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** One short bar per message, filled to how much of the network's length it takes. */
@Composable
private fun ShareBars(parts: List<MessageSplitter.Part>, budget: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.width(44.dp)) {
        parts.take(MAX_BARS).forEach { part ->
            Box(
                Modifier
                    .weight(1f)
                    .height(6.dp)
                    .background(MaterialTheme.colorScheme.outline, CircleShape)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth((part.text.length.toFloat() / budget).coerceIn(0f, 1f))
                        .height(6.dp)
                        .background(MaterialTheme.colorScheme.onTertiaryContainer, CircleShape)
                )
            }
        }
    }
}

/**
 * Where the draft splits before it is sent: each message by its opening and its end, and why each
 * cut fell where it did. [instead] names a network in the thread that takes it whole, if any.
 */
@Composable
internal fun SplitPreview(
    parts: List<MessageSplitter.Part>,
    network: String,
    budget: Int,
    instead: String?,
    onSendInstead: () -> Unit,
    onClose: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val count = NumberFormat.getIntegerInstance()
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = colors.surfaceContainer,
        modifier = Modifier.padding(bottom = 4.dp)
    ) {
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(14.dp), color = colors.tertiaryContainer) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        Icon(Symbols.CallSplit, contentDescription = null, tint = colors.onTertiaryContainer)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        pluralStringResource(R.plurals.split_preview_title, parts.size, parts.size),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        stringResource(R.string.split_preview_subtitle, network, count.format(budget)),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Symbols.Close, contentDescription = stringResource(R.string.split_preview_close))
                }
            }
            Column(Modifier.padding(top = 10.dp).heightIn(max = 244.dp).verticalScroll(rememberScrollState())) {
                parts.forEachIndexed { index, part ->
                    PartRow(index + 1, part.text, count.format(part.text.length))
                    part.cut?.let { CutRail(it) }
                }
            }
            if (instead != null) {
                TextButton(onClick = onSendInstead) { Text(stringResource(R.string.split_send_instead, instead)) }
            }
        }
    }
}

@Composable
private fun PartRow(number: Int, text: String, length: String) {
    val colors = MaterialTheme.colorScheme
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainerHighest, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Surface(shape = CircleShape, color = colors.primary, contentColor = colors.onPrimary) {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                Text(number.toString(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
        Column(Modifier.weight(1f)) {
            val opening = text.lineSequence().first()
            Text(opening, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (text.length > opening.length || text.length > ENDING) {
                Text(
                    "… " + text.takeLast(ENDING).replace('\n', ' ').trimStart(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(length, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
    }
}

/** The seam between two messages, and why it fell there. */
@Composable
private fun CutRail(cut: MessageSplitter.Cut) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.height(26.dp).padding(horizontal = 12.dp)
    ) {
        Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.width(2.dp).height(26.dp).background(colors.outlineVariant, CircleShape))
        }
        Text(
            stringResource(cut.label),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurfaceVariant
        )
    }
}

private val MessageSplitter.Cut.label: Int
    get() = when (this) {
        MessageSplitter.Cut.Paragraph -> R.string.split_cut_paragraph
        MessageSplitter.Cut.Line -> R.string.split_cut_line
        MessageSplitter.Cut.Word -> R.string.split_cut_word
        MessageSplitter.Cut.MidWord -> R.string.split_cut_mid_word
    }

/** How much of a message's end the preview shows, enough to see where it stops. */
private const val ENDING = 50
private const val MAX_BARS = 6
