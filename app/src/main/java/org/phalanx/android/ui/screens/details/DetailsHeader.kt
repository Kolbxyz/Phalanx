package org.phalanx.android.ui.screens.details

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.data.model.RoomSummary
import org.phalanx.android.data.model.UserTrust
import org.phalanx.android.ui.components.MatrixAvatar
import org.phalanx.android.ui.components.TrustLabel
import org.phalanx.android.ui.theme.Symbols

/** The room's face: a large shaped avatar, its name, topic and who is in it, then quick toggles. */
@Composable
internal fun DetailsHeader(
    room: RoomSummary,
    trust: UserTrust?,
    onFavourite: (Boolean) -> Unit,
    onUnread: (Boolean) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        MatrixAvatar(
            name = room.name,
            media = room.avatar,
            size = 112.dp,
            shape = if (room.isDirect) MaterialShapes.Cookie9Sided.toShape() else MaterialShapes.Cookie4Sided.toShape(),
            container = if (room.isDirect) colors.tertiaryContainer else colors.primaryContainer,
            content = if (room.isDirect) colors.onTertiaryContainer else colors.onPrimaryContainer,
            textStyle = MaterialTheme.typography.displaySmall
        )
        Text(
            text = room.name,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, start = 24.dp, end = 24.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (room.isEncrypted) Icon(Symbols.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(
                text = buildString {
                    if (!room.isDirect) {
                        val count = room.memberCount
                        append(pluralStringResource(R.plurals.details_members, count.toInt(), count))
                    }
                    if (room.isEncrypted) {
                        if (isNotEmpty()) append(" · ")
                        append(stringResource(R.string.details_encrypted))
                    }
                },
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant
            )
        }
        trust?.let { TrustLabel(it) }
        room.topic?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 4,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            QuickToggle(
                checked = room.isFavourite,
                icon = if (room.isFavourite) Symbols.Star else Symbols.StarOutline,
                label = R.string.details_favourite,
                onToggle = onFavourite
            )
            QuickToggle(
                checked = room.hasUnread,
                icon = if (room.hasUnread) Symbols.MarkChatUnread else Symbols.MarkChatRead,
                label = R.string.details_unread,
                onToggle = onUnread
            )
        }
    }
}

/** A large round toggle with its label underneath; it morphs to a square-ish shape when on. */
@Composable
private fun QuickToggle(checked: Boolean, icon: ImageVector, @StringRes label: Int, onToggle: (Boolean) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FilledTonalIconToggleButton(checked = checked, onCheckedChange = onToggle, modifier = Modifier.size(56.dp)) {
            Icon(icon, contentDescription = null)
        }
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
    }
}
