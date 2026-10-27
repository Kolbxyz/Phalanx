package org.phalanx.android.ui.screens.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.phalanx.android.R
import org.phalanx.android.ui.components.SegmentedSection
import org.phalanx.android.ui.screens.settings.SwitchGroup
import org.phalanx.android.ui.screens.settings.SwitchSetting
import org.phalanx.android.ui.screens.settings.labelOf
import org.phalanx.android.ui.theme.Symbols

/**
 * Auto-translate for this room: on, its messages show in the chosen pair's target language, the
 * original a tap away. The pairs are downloaded in AI settings.
 */
@Composable
internal fun DetailsTranslateSection(viewModel: RoomTranslateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val failed by viewModel.failed.collectAsStateWithLifecycle()
    SegmentedSection(stringResource(R.string.details_translate_section)) {
        SwitchGroup(
            listOf(
                SwitchSetting(
                    Symbols.Translate,
                    R.string.details_auto_translate,
                    if (state.installed.isEmpty()) {
                        R.string.details_auto_translate_needs_pair
                    } else {
                        R.string.details_auto_translate_summary
                    },
                    state.chosen != null,
                    viewModel::setEnabled
                )
            )
        )
        val chosen = state.chosen
        if (chosen != null && state.installed.size > 1) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
            ) {
                state.installed.forEach { pair ->
                    val selected = pair == chosen
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.choose(pair) },
                        label = { Text(labelOf(pair)) },
                        leadingIcon = if (selected) {
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
        if (failed) {
            Text(
                stringResource(R.string.timeline_action_failed),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}
