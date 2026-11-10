package org.phalanx.android.ui.screens.room

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.data.model.Member
import org.phalanx.android.data.model.RoomSummary
import org.phalanx.android.data.model.UserTrust
import org.phalanx.android.ui.components.CopyIdChip
import org.phalanx.android.ui.components.HaloAvatar
import org.phalanx.android.ui.components.InlineConfirm
import org.phalanx.android.ui.components.MatrixAvatar
import org.phalanx.android.ui.components.MenuAction
import org.phalanx.android.ui.components.MenuActions
import org.phalanx.android.ui.components.TrustLabel
import org.phalanx.android.ui.components.riseIn
import org.phalanx.android.ui.screens.details.AlsoOnChips
import org.phalanx.android.ui.theme.Symbols

/**
 * Someone's profile from the conversation: a warning first if their identity changed, then who
 * they are and where else the user talks with them, and what to do: write to them, mention them
 * here, mute them, or block them, asking in place.
 */
@Composable
internal fun QuickProfile(
    member: Member,
    trust: UserTrust?,
    alsoOn: List<RoomSummary>,
    muted: Boolean,
    onViewAvatar: () -> Unit,
    onOpenChat: (roomId: String) -> Unit,
    onMessage: () -> Unit,
    onMention: () -> Unit,
    onToggleMute: () -> Unit,
    onBlock: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val changed = trust == UserTrust.Changed
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        if (changed) IdentityChanged()
        HaloAvatar(
            haloSize = 124.dp,
            halo = if (changed) colors.errorContainer else colors.tertiaryContainer,
            modifier = Modifier.clickable(
                enabled = member.avatar != null,
                onClickLabel = stringResource(R.string.profile_photo),
                role = Role.Image,
                onClick = onViewAvatar
            )
        ) {
            MatrixAvatar(
                name = member.name,
                media = member.avatar,
                size = 88.dp,
                shape = MaterialShapes.Cookie9Sided.toShape(),
                container = if (changed) colors.error else colors.tertiary,
                content = if (changed) colors.onError else colors.onTertiary,
                textStyle = MaterialTheme.typography.headlineMedium
            )
        }
        Text(
            member.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        trust?.let { TrustLabel(it) }
        CopyIdChip(member.userId, stringResource(R.string.profile_copy_their_id))
        if (alsoOn.isNotEmpty()) AlsoOnChips(alsoOn, onOpenChat)
    }
    MenuActions(
        actions = listOf(
            MenuAction(Symbols.Chat, R.string.profile_message, onMessage, weight = MESSAGE_WEIGHT, primary = true),
            MenuAction(Symbols.AlternateEmail, R.string.profile_mention, onMention),
            MenuAction(
                if (muted) Symbols.NotificationsOff else Symbols.NotificationsActive,
                R.string.menu_mute,
                onToggleMute,
                muted
            )
        ),
        footer = {
            InlineConfirm(
                icon = Symbols.Block,
                label = stringResource(R.string.action_block),
                question = stringResource(R.string.block_title, member.name.substringBefore(' ')),
                body = stringResource(R.string.block_body),
                confirmLabel = stringResource(R.string.action_block),
                onConfirm = onBlock
            )
        }
    )
}

/** Said before anything else: a new identity may not be them at all. */
@Composable
private fun IdentityChanged() {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.errorContainer,
        contentColor = colors.onErrorContainer,
        modifier = Modifier.fillMaxWidth().riseIn(0L)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(16.dp)) {
            Box(
                Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) { Icon(Symbols.GppMaybe, contentDescription = null) }
            Column {
                Text(stringResource(R.string.trust_changed), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.trust_changed_body), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

// The way the sheet is mostly opened for, so it takes the most room.
private const val MESSAGE_WEIGHT = 1.5f
