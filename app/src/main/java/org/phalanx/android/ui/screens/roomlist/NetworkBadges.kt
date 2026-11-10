package org.phalanx.android.ui.screens.roomlist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.phalanx.android.data.model.Bridge

/**
 * The networks a chat is on, as small logos overlapping at the avatar's foot, ringed in the row's
 * [ring] colour so they read as cut out of it. Nothing for a chat on Matrix alone.
 */
@Composable
internal fun NetworkBadges(networks: List<Bridge>, ring: Color, modifier: Modifier = Modifier) {
    if (networks.isEmpty()) return
    val description = networks.map { stringResource(it.label) }.joinToString()
    Box(modifier.semantics { contentDescription = description }) {
        networks.asReversed().forEachIndexed { reversed, bridge ->
            val index = networks.size - 1 - reversed
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(2.dp, ring),
                modifier = Modifier.offset(x = -STEP * index).size(BADGE)
            ) {
                Box(contentAlignment = Alignment.Center) { SourceIcon(RoomSource.Network(bridge), ICON) }
            }
        }
    }
}

private val BADGE = 20.dp
private val ICON = 12.dp
private val STEP = 14.dp
