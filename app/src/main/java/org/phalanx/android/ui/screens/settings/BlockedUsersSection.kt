package org.phalanx.android.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.phalanx.android.R
import org.phalanx.android.ui.components.SegmentedRow
import org.phalanx.android.ui.components.SegmentedSection
import org.phalanx.android.ui.components.segmentedShape

/**
 * The account's blocked users, each with an unblock button; one quiet row when there are none.
 * The list lives on the server, so it is the same on every device.
 */
@Composable
internal fun BlockedUsersSection(blocked: List<String>?, onUnblock: (String) -> Unit) {
    SegmentedSection(stringResource(R.string.settings_blocked_users)) {
        val users = blocked.orEmpty()
        if (users.isEmpty()) {
            SegmentedRow(
                headline = stringResource(if (blocked == null) R.string.loading else R.string.settings_blocked_none),
                supporting = stringResource(R.string.settings_blocked_summary),
                shape = segmentedShape(0, 1),
                leading = { Icon(Icons.Rounded.Block, contentDescription = null) }
            )
        }
        users.forEachIndexed { index, userId ->
            SegmentedRow(
                headline = userId,
                shape = segmentedShape(index, users.size),
                leading = { Icon(Icons.Rounded.Block, contentDescription = null) },
                trailing = {
                    TextButton(onClick = { onUnblock(userId) }) { Text(stringResource(R.string.settings_unblock)) }
                }
            )
        }
    }
}
