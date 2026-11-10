package org.phalanx.android.ui.screens.roomlist

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.phalanx.android.ui.components.InitialAvatar
import org.phalanx.android.ui.theme.Symbols

/** The network's logo, in the theme's colour like any icon; its initial for one without a logo. */
@Composable
internal fun SourceIcon(source: RoomSource, size: Dp = 24.dp) {
    val logo = (source as? RoomSource.Network)?.bridge?.logo
    when {
        source == RoomSource.All -> Icon(Symbols.Chat, contentDescription = null, Modifier.size(size))
        source == RoomSource.Matrix -> Icon(Symbols.Hub, contentDescription = null, Modifier.size(size))
        logo != null -> Icon(painterResource(logo), contentDescription = null, Modifier.size(size))
        else -> {
            val name = stringResource(source.label)
            InitialAvatar(
                name = name,
                shape = CircleShape,
                size = size,
                container = MaterialTheme.colorScheme.tertiaryContainer,
                content = MaterialTheme.colorScheme.onTertiaryContainer,
                textStyle = MaterialTheme.typography.labelMedium
            )
        }
    }
}
