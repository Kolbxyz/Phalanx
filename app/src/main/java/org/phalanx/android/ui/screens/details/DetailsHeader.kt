package org.phalanx.android.ui.screens.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.phalanx.android.R
import org.phalanx.android.data.model.RoomSummary
import org.phalanx.android.data.model.UserTrust
import org.phalanx.android.ui.components.CopyIdChip
import org.phalanx.android.ui.components.HaloAvatar
import org.phalanx.android.ui.components.MatrixAvatar
import org.phalanx.android.ui.components.MenuAction
import org.phalanx.android.ui.components.QuickButton
import org.phalanx.android.ui.theme.Symbols

/**
 * The conversation's face: its avatar on a halo, the name, whether the person is verified and the
 * chat encrypted, the person's Matrix ID to copy, then the quick toggles that stay on screen.
 */
@Composable
internal fun DetailsHeader(
    room: RoomSummary,
    trust: UserTrust?,
    onFavourite: (Boolean) -> Unit,
    onUnread: (Boolean) -> Unit,
    onMuted: (Boolean) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val person = room.isDirect
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HaloAvatar(haloSize = 148.dp, halo = if (person) colors.tertiaryContainer else colors.primaryContainer) {
            MatrixAvatar(
                name = room.name,
                media = room.avatar,
                size = 108.dp,
                shape = (if (person) MaterialShapes.Cookie9Sided else MaterialShapes.Cookie4Sided).toShape(),
                container = if (person) colors.tertiary else colors.primary,
                content = if (person) colors.onTertiary else colors.onPrimary,
                textStyle = MaterialTheme.typography.displayMedium
            )
        }
        Text(
            text = room.name,
            fontSize = 30.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.4).sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            trust?.let { TrustChip(it) }
            if (!person) {
                val count = room.memberCount
                InfoChip(Symbols.Chat, pluralStringResource(R.plurals.details_members, count.toInt(), count))
            }
            if (room.isEncrypted) InfoChip(Symbols.Lock, stringResource(R.string.details_encrypted))
        }
        room.dmUserId?.let { CopyIdChip(it, stringResource(R.string.profile_copy_their_id), outlined = true) }
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
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
        ) {
            val toggles = listOf(
                MenuAction(
                    if (room.isFavourite) Symbols.Star else Symbols.StarOutline,
                    R.string.details_favourite,
                    { onFavourite(!room.isFavourite) },
                    room.isFavourite
                ),
                MenuAction(
                    if (room.hasUnread) Symbols.MarkChatUnread else Symbols.MarkChatRead,
                    R.string.details_unread,
                    { onUnread(!room.hasUnread) },
                    room.hasUnread
                ),
                MenuAction(
                    if (room.isMuted) Symbols.NotificationsOff else Symbols.Notifications,
                    R.string.details_muted,
                    { onMuted(!room.isMuted) },
                    room.isMuted
                )
            )
            toggles.forEach { QuickButton(it, lead = false, height = 80.dp) }
        }
    }
}

/** Verified in the primary container, a changed identity in the error one. */
@Composable
private fun TrustChip(trust: UserTrust) {
    val colors = MaterialTheme.colorScheme
    when (trust) {
        UserTrust.Verified ->
            InfoChip(
                Symbols.VerifiedUser,
                stringResource(R.string.trust_verified),
                colors.primaryContainer,
                colors.onPrimaryContainer
            )
        UserTrust.Unverified -> InfoChip(Symbols.Shield, stringResource(R.string.trust_unverified))
        UserTrust.Changed ->
            InfoChip(
                Symbols.GppMaybe,
                stringResource(R.string.trust_changed_short),
                colors.errorContainer,
                colors.onErrorContainer
            )
    }
}

@Composable
private fun InfoChip(
    icon: ImageVector,
    label: String,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(shape = CircleShape, color = container, contentColor = content, modifier = Modifier.heightIn(min = 28.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
