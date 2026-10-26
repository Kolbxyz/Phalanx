package org.phalanx.android.ui.screens.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.data.model.NotificationContent
import org.phalanx.android.ui.components.ConnectedToggleGroup
import org.phalanx.android.ui.components.SegmentedSection

/** How much a notification shows, for anyone who can see the phone's screen. */
@Composable
internal fun NotificationContentSection(selected: NotificationContent, onSelect: (NotificationContent) -> Unit) {
    SegmentedSection(stringResource(R.string.push_content_section)) {
        ConnectedToggleGroup(
            options = NotificationContent.entries,
            selected = selected,
            label = { stringResource(it.labelRes()) },
            onSelect = onSelect
        )
        Text(
            text = stringResource(selected.summaryRes()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp)
        )
    }
}

@StringRes
private fun NotificationContent.labelRes(): Int = when (this) {
    NotificationContent.Full -> R.string.push_content_full
    NotificationContent.NameOnly -> R.string.push_content_name
    NotificationContent.Hidden -> R.string.push_content_hidden
}

@StringRes
private fun NotificationContent.summaryRes(): Int = when (this) {
    NotificationContent.Full -> R.string.push_content_full_summary
    NotificationContent.NameOnly -> R.string.push_content_name_summary
    NotificationContent.Hidden -> R.string.push_content_hidden_summary
}
