package org.phalanx.android.ui.screens.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import org.phalanx.android.ui.components.SegmentedRow
import org.phalanx.android.ui.components.segmentedShape

internal class SwitchSetting(
    val icon: ImageVector,
    @StringRes val title: Int,
    @StringRes val summary: Int,
    val checked: Boolean,
    val onChange: (Boolean) -> Unit
)

/** A run of switch rows drawn as one segmented card. */
@Composable
internal fun SwitchGroup(items: List<SwitchSetting>) {
    items.forEachIndexed { index, item ->
        SegmentedRow(
            headline = stringResource(item.title),
            supporting = stringResource(item.summary),
            shape = segmentedShape(index, items.size),
            leading = { Icon(item.icon, contentDescription = null) },
            // The whole row toggles; the switch itself only shows the state, so TalkBack reads one control.
            interaction = Modifier.toggleable(value = item.checked, role = Role.Switch, onValueChange = item.onChange),
            trailing = {
                Switch(
                    checked = item.checked,
                    onCheckedChange = null,
                    // Expressive switches carry a check in the thumb when on.
                    thumbContent = if (item.checked) {
                        { Icon(Icons.Rounded.Check, null, Modifier.size(SwitchDefaults.IconSize)) }
                    } else {
                        null
                    }
                )
            }
        )
    }
}
