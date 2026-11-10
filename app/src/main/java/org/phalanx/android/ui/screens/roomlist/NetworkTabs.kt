package org.phalanx.android.ui.screens.roomlist

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.phalanx.android.R

/**
 * Where the rooms come from, as tabs under the title: All, Matrix, then each network, busiest
 * first. Each says how many unread messages wait in it, so a quiet network needs no visit.
 */
@Composable
internal fun NetworkTabs(sources: List<SourceCount>, selected: RoomSource, onSelect: (RoomSource) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        modifier = Modifier.height(44.dp)
    ) {
        items(sources, key = { it.source.toString() }) { count ->
            NetworkTab(count, count.source == selected) { onSelect(count.source) }
        }
    }
}

@Composable
private fun NetworkTab(count: SourceCount, isSelected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val container by animateColorAsState(if (isSelected) colors.secondaryContainer else colors.surface, effects)
    val content by animateColorAsState(
        if (isSelected) colors.onSecondaryContainer else colors.onSurfaceVariant,
        effects
    )
    val label = stringResource(if (count.source == RoomSource.All) R.string.roomlist_filter_all else count.source.label)
    val unread = count.unread.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    val description = if (unread > 0) {
        label + ", " + pluralStringResource(R.plurals.roomlist_unread, unread, unread)
    } else {
        label
    }
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = container,
        contentColor = content,
        modifier = Modifier
            .height(40.dp)
            .semantics(mergeDescendants = true) {
                role = Role.Tab
                selected = isSelected
                contentDescription = description
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 14.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
            if (unread > 0) TabCount(unread, isSelected)
        }
    }
}

@Composable
private fun TabCount(unread: Int, isSelected: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = if (isSelected) colors.primary else colors.surfaceContainerHighest,
        contentColor = if (isSelected) colors.onPrimary else colors.onSurface
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.defaultMinSize(minWidth = 20.dp, minHeight = 20.dp).padding(horizontal = 6.dp)
        ) {
            Text(
                text = if (unread > MAX_COUNT) "$MAX_COUNT+" else unread.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private const val MAX_COUNT = 99
