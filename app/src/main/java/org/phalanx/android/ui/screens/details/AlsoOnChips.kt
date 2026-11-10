package org.phalanx.android.ui.screens.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.data.model.RoomSummary
import org.phalanx.android.ui.screens.people.sourceOf
import org.phalanx.android.ui.screens.roomlist.SourceIcon
import org.phalanx.android.ui.screens.roomlist.label
import org.phalanx.android.ui.theme.Symbols

/**
 * The same person's or group's chats on other networks, each a chip that opens it. [onLink], when
 * given, ends the row with a dashed chip to link one more, dashed as it is not a chat yet.
 */
@Composable
internal fun AlsoOnChips(
    chats: List<RoomSummary>,
    onOpen: (roomId: String) -> Unit,
    onLink: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        chats.forEach { chat ->
            val source = sourceOf(chat)
            NetworkChip(stringResource(source.label), onClick = { onOpen(chat.id) }) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { SourceIcon(source, size = 14.dp) }
                }
            }
        }
        onLink?.let { link ->
            val outline = MaterialTheme.colorScheme.outline
            NetworkChip(
                stringResource(R.string.details_link_chat),
                onClick = link,
                container = Color.Transparent,
                modifier = Modifier.drawBehind {
                    drawRoundRect(
                        color = outline,
                        cornerRadius = CornerRadius(size.height / 2),
                        style = Stroke(
                            width = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH, DASH))
                        )
                    )
                }
            ) { Icon(Symbols.Link, contentDescription = null, modifier = Modifier.size(18.dp)) }
        }
    }
}

@Composable
private fun NetworkChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    leading: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = container,
        modifier = modifier.heightIn(min = 36.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp)
        ) {
            leading()
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private const val DASH = 12f
