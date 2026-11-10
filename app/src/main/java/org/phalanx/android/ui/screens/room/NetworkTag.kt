package org.phalanx.android.ui.screens.room

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.phalanx.android.data.model.RoomSummary
import org.phalanx.android.ui.screens.people.sourceOf
import org.phalanx.android.ui.screens.roomlist.SourceIcon
import org.phalanx.android.ui.screens.roomlist.label

/** The person's thread the timeline belongs to; null in any other room. */
internal val LocalPersonThread = compositionLocalOf<PersonThread?> { null }

/** Above the first message of a run from one network: which network it came through. */
@Composable
internal fun NetworkTag(chat: RoomSummary, isOwn: Boolean) {
    val source = sourceOf(chat)
    Box(
        contentAlignment = if (isOwn) Alignment.CenterEnd else Alignment.CenterStart,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                SourceIcon(source, size = 14.dp)
                Text(
                    stringResource(source.label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
