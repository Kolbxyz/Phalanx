package org.phalanx.android.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.data.model.AppSettings
import org.phalanx.android.ui.components.SegmentGap
import org.phalanx.android.ui.components.SegmentedRow
import org.phalanx.android.ui.components.SegmentedSection
import org.phalanx.android.ui.components.segmentedShape
import org.phalanx.android.ui.theme.Symbols

/** Edit history and kept deleted messages, both off unless the user turns them on. */
@Composable
internal fun MessageHistorySection(
    current: AppSettings,
    viewModel: SettingsViewModel,
    onOpenDeletedMessages: () -> Unit
) {
    SegmentedSection(stringResource(R.string.settings_message_history)) {
        Column(verticalArrangement = Arrangement.spacedBy(SegmentGap)) {
            SwitchGroup(
                listOf(
                    SwitchSetting(Symbols.History, R.string.settings_edit_history,
                        R.string.settings_edit_history_summary, current.showEditHistory,
                        viewModel::setShowEditHistory),
                    SwitchSetting(Symbols.DeleteHistory, R.string.settings_keep_deleted,
                        R.string.settings_keep_deleted_summary, current.keepDeletedMessages,
                        viewModel::setKeepDeletedMessages)
                )
            )
        }
        if (current.keepDeletedMessages) {
            Spacer(Modifier.height(8.dp))
            SegmentedRow(
                headline = stringResource(R.string.settings_deleted_messages),
                supporting = stringResource(R.string.settings_deleted_messages_summary),
                shape = segmentedShape(0, 1),
                leading = { Icon(Symbols.Delete, contentDescription = null) },
                interaction = Modifier.clickable(role = Role.Button, onClick = onOpenDeletedMessages)
            )
        }
    }
}
