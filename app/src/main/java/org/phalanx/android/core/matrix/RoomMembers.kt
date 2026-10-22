package org.phalanx.android.core.matrix

import org.matrix.rustcomponents.sdk.MembershipState
import org.matrix.rustcomponents.sdk.RoomMember
import org.matrix.rustcomponents.sdk.RoomMembersIterator
import org.phalanx.android.data.model.Member
import org.phalanx.android.data.model.MemberRole
import org.phalanx.android.data.model.Participant
import uniffi.matrix_sdk.RoomMemberRole

private const val CHUNK = 200u

// A bridged server can hold tens of thousands; past this the list is for scrolling, not reading.
private const val MAX_MEMBERS = 2000

/** Joined and invited members, highest role first, then invitations last, then by name. */
internal fun RoomMembersIterator.participants(): List<Participant> {
    val all = ArrayList<Participant>()
    while (all.size < MAX_MEMBERS) {
        val chunk = nextChunk(CHUNK) ?: break
        chunk.mapNotNullTo(all) { it.toParticipant() }
    }
    return all.sortedWith(
        compareBy<Participant>({ it.role }, { it.invited }, { it.member.name.lowercase() })
    )
}

private fun RoomMember.toParticipant(): Participant? {
    val invited = when (membership) {
        MembershipState.Join -> false
        MembershipState.Invite -> true
        else -> return null
    }
    val role = when (suggestedRoleForPowerLevel) {
        RoomMemberRole.CREATOR, RoomMemberRole.ADMINISTRATOR -> MemberRole.Admin
        RoomMemberRole.MODERATOR -> MemberRole.Moderator
        RoomMemberRole.USER -> MemberRole.Member
    }
    val name = displayName?.takeIf { it.isNotBlank() } ?: userId
    return Participant(Member(userId, name, mediaRefOf(avatarUrl)), role, invited)
}
