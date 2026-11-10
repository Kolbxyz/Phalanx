package org.phalanx.android.ui.screens.roomlist

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.ui.theme.Symbols

/**
 * The room list's side panel, swiped in from the left: the places that gather messages from every
 * chat (the reminders still to come, the saved messages and the people linked across networks),
 * then the settings. Networks themselves are the tabs above the list.
 */
@Composable
internal fun RoomListDrawer(
    drawerState: DrawerState,
    enabled: Boolean,
    reminders: Int,
    onOpenReminders: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenPeople: () -> Unit,
    onOpenSettings: () -> Unit,
    content: @Composable () -> Unit
) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = enabled,
        drawerContent = {
            ModalDrawerSheet(drawerState = drawerState) {
                Spacer(Modifier.height(12.dp))
                DrawerItem(Symbols.AlarmOn, R.string.reminders_title, onOpenReminders, reminders)
                DrawerItem(Symbols.Bookmark, R.string.bookmarks_title, onOpenBookmarks)
                DrawerItem(Symbols.Link, R.string.people_title, onOpenPeople)
                HorizontalDivider(Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
                DrawerItem(Symbols.Settings, R.string.settings_title, onOpenSettings)
            }
        },
        content = content
    )
}

@Composable
private fun DrawerItem(icon: ImageVector, label: Int, onClick: () -> Unit, count: Int = 0) {
    NavigationDrawerItem(
        label = { Text(stringResource(label)) },
        selected = false,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null, Modifier.size(24.dp)) },
        badge = { if (count > 0) Text(count.toString()) },
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
}
