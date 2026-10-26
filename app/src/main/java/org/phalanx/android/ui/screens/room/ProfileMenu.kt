package org.phalanx.android.ui.screens.room

import android.content.ClipData
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.phalanx.android.R
import org.phalanx.android.data.model.Member
import org.phalanx.android.data.model.UserTrust
import org.phalanx.android.ui.components.MatrixAvatar
import org.phalanx.android.ui.components.ModalSheet
import org.phalanx.android.ui.components.TrustLabel
import org.phalanx.android.ui.theme.Symbols

/** The profile of [userId], opened from a mention or a sender, and the block confirmation it leads to. */
@Composable
internal fun ProfileMenu(
    userId: String?,
    onDismiss: () -> Unit,
    onOpenRoom: (roomId: String) -> Unit,
    viewModel: RoomViewModel,
    profiles: ProfileViewModel = hiltViewModel()
) {
    var blocking by remember { mutableStateOf<Member?>(null) }
    var viewing by remember { mutableStateOf<Member?>(null) }
    val muted by viewModel.muted.collectAsStateWithLifecycle()
    if (userId != null) {
        // The ID stands in for the name until the room says who they are.
        val member by produceState(Member(userId, userId, null), userId) { value = profiles.member(userId) }
        val trust by produceState<UserTrust?>(null, userId) { value = profiles.trustOf(userId) }
        ProfileSheet(
            member = member,
            trust = trust,
            muted = userId in muted,
            onDismiss = onDismiss,
            onViewAvatar = { viewing = member },
            onMessage = {
                onDismiss()
                profiles.message(
                    userId,
                    // Already their direct chat: closing the profile is all it takes.
                    onOpened = { roomId -> if (roomId != viewModel.roomId) onOpenRoom(roomId) },
                    onFailed = { viewModel.notify(R.string.profile_message_failed) }
                )
            },
            onToggleMute = {
                viewModel.setMuted(userId, userId !in muted)
                onDismiss()
            },
            onBlock = {
                blocking = member
                onDismiss()
            }
        )
    }
    viewing?.let { target ->
        target.avatar?.let { AvatarViewer(it, target.name, onDismiss = { viewing = null }) }
    }
    blocking?.let { target ->
        BlockUserDialog(
            name = target.name,
            onConfirm = {
                viewModel.block(target.userId)
                blocking = null
            },
            onDismiss = { blocking = null }
        )
    }
}

@Composable
private fun ProfileSheet(
    member: Member,
    trust: UserTrust?,
    muted: Boolean,
    onDismiss: () -> Unit,
    onViewAvatar: () -> Unit,
    onMessage: () -> Unit,
    onToggleMute: () -> Unit,
    onBlock: () -> Unit
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    ModalSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val viewLabel = stringResource(R.string.profile_photo)
            MatrixAvatar(
                member.name,
                member.avatar,
                96.dp,
                modifier = if (member.avatar == null) {
                    Modifier
                } else {
                    Modifier
                        .clip(CircleShape)
                        .clickable(onClickLabel = viewLabel, role = Role.Image, onClick = onViewAvatar)
                }
            )
            Spacer(Modifier.height(8.dp))
            Text(member.name, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            if (member.name != member.userId) {
                Text(
                    text = member.userId,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        trust?.let {
            TrustLabel(it, Modifier.fillMaxWidth().padding(top = 4.dp).wrapContentWidth(Alignment.CenterHorizontally))
        }
        Spacer(Modifier.height(8.dp))
        ProfileAction(Symbols.Chat, R.string.profile_message, onClick = onMessage)
        ProfileAction(Symbols.ContentCopy, R.string.profile_copy_id) {
            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, member.userId))) }
            onDismiss()
        }
        if (muted) {
            ProfileAction(Symbols.NotificationsActive, R.string.action_unmute, onClick = onToggleMute)
        } else {
            ProfileAction(Symbols.NotificationsOff, R.string.action_mute, onClick = onToggleMute)
        }
        ProfileAction(Symbols.Block, R.string.action_block, MaterialTheme.colorScheme.error, onBlock)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ProfileAction(
    icon: ImageVector,
    @StringRes label: Int,
    color: Color = Color.Unspecified,
    onClick: () -> Unit
) {
    val tint = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color
    ListItem(
        headlineContent = { Text(stringResource(label)) },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(headlineColor = tint, leadingIconColor = tint),
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick)
    )
}
