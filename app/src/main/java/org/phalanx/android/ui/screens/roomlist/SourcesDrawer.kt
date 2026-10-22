package org.phalanx.android.ui.screens.roomlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.ui.components.InitialAvatar
import org.phalanx.android.ui.theme.Symbols

/**
 * The room list's side panel, swiped in from the left: every source the rooms come from (Matrix
 * itself and each bridged network) with how many rooms it holds. Picking one narrows the list.
 */
@Composable
internal fun SourcesDrawer(
    drawerState: DrawerState,
    sources: List<SourceCount>,
    selected: RoomSource,
    onSelect: (RoomSource) -> Unit,
    content: @Composable () -> Unit
) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = sources.isNotEmpty(),
        drawerContent = {
            ModalDrawerSheet(drawerState = drawerState) {
                Text(
                    text = stringResource(R.string.sources_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp).semantics { heading() }
                )
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    sources.forEach { (source, count) ->
                        NavigationDrawerItem(
                            label = { Text(stringResource(source.label)) },
                            selected = source == selected,
                            onClick = { onSelect(source) },
                            icon = { SourceIcon(source) },
                            badge = { Text(count.toString()) },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }
                }
            }
        },
        content = content
    )
}

@Composable
private fun SourceIcon(source: RoomSource) {
    when (source) {
        RoomSource.All -> Icon(Symbols.Chat, contentDescription = null)
        RoomSource.Matrix -> Icon(Symbols.Hub, contentDescription = null)
        // No brand logos ship in the app; the network's initial stands in, like a contact's.
        is RoomSource.Network -> {
            val name = stringResource(source.label)
            InitialAvatar(
                name = name,
                shape = CircleShape,
                size = 24.dp,
                container = MaterialTheme.colorScheme.tertiaryContainer,
                content = MaterialTheme.colorScheme.onTertiaryContainer,
                textStyle = MaterialTheme.typography.labelMedium
            )
        }
    }
}
