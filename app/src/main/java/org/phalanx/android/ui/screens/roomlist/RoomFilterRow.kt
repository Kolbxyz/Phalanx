package org.phalanx.android.ui.screens.roomlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.ui.theme.Symbols

/**
 * One chip per [RoomFilter]; Archived says how many rooms it holds. A source picked in the side
 * panel leads the row as a chip that clears it.
 */
@Composable
internal fun RoomFilterRow(
    selected: RoomFilter,
    archivedCount: Int,
    onSelect: (RoomFilter) -> Unit,
    source: RoomSource,
    onClearSource: () -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        if (source != RoomSource.All) {
            item(key = "source") {
                InputChip(
                    selected = true,
                    onClick = onClearSource,
                    label = { Text(stringResource(source.label)) },
                    leadingIcon = { SourceIcon(source, InputChipDefaults.AvatarSize) },
                    trailingIcon = {
                        Icon(
                            Symbols.Close,
                            contentDescription = stringResource(R.string.sources_clear),
                            modifier = Modifier.size(InputChipDefaults.IconSize)
                        )
                    },
                    modifier = Modifier.animateItem()
                )
            }
        }
        items(RoomFilter.entries, key = { it.name }) { filter ->
            val isSelected = filter == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(if (isSelected) RoomFilter.All else filter) },
                label = {
                    Text(
                        if (filter == RoomFilter.Archived && archivedCount > 0) {
                            stringResource(R.string.roomlist_filter_archived_count, archivedCount)
                        } else {
                            stringResource(filter.label)
                        }
                    )
                },
                leadingIcon = if (isSelected) {
                    {
                        Icon(
                            Symbols.Check,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                    }
                } else {
                    null
                }
            )
        }
    }
}
