package org.phalanx.android.ui.screens.room

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.phalanx.android.R
import org.phalanx.android.data.model.Member
import org.phalanx.android.data.model.UserTrust
import org.phalanx.android.ui.components.ModalSheet

/** The profile of [userId], opened from a mention or a sender. */
@Composable
internal fun ProfileMenu(
    userId: String?,
    onDismiss: () -> Unit,
    onOpenRoom: (roomId: String) -> Unit,
    viewModel: RoomViewModel,
    profiles: ProfileViewModel = hiltViewModel()
) {
    var viewing by remember { mutableStateOf<Member?>(null) }
    val muted by viewModel.muted.collectAsStateWithLifecycle()
    if (userId != null) {
        // The ID stands in for the name until the room says who they are.
        val member by produceState(Member(userId, userId, null), userId) { value = profiles.member(userId) }
        val trust by produceState<UserTrust?>(null, userId) { value = profiles.trustOf(userId) }
        val alsoOn by remember(userId) { profiles.alsoOn(userId) }.collectAsStateWithLifecycle(emptyList())
        ModalSheet(onDismissRequest = onDismiss) {
            QuickProfile(
                member = member,
                trust = trust,
                alsoOn = alsoOn,
                muted = userId in muted,
                onViewAvatar = { viewing = member },
                onOpenChat = { roomId ->
                    onDismiss()
                    onOpenRoom(roomId)
                },
                onMessage = {
                    onDismiss()
                    profiles.message(
                        userId,
                        // Already their direct chat: closing the profile is all it takes.
                        onOpened = { roomId -> if (roomId != viewModel.roomId) onOpenRoom(roomId) },
                        onFailed = { viewModel.notify(R.string.profile_message_failed) }
                    )
                },
                onMention = {
                    viewModel.composer.mention(member)
                    onDismiss()
                },
                // Stays open: the button shows the new state, and it can be flipped back.
                onToggleMute = { viewModel.setMuted(userId, userId !in muted) },
                onBlock = {
                    viewModel.block(userId)
                    onDismiss()
                }
            )
        }
    }
    viewing?.let { target ->
        target.avatar?.let { AvatarViewer(it, target.name, onDismiss = { viewing = null }) }
    }
}
