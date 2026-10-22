package org.phalanx.android.data.model

import androidx.compose.runtime.Immutable

/** A room member as shown in a list: [name] is their display name, or their Matrix ID without one. */
@Immutable
data class Member(val userId: String, val name: String, val avatar: MediaRef?)

/** Highest first, the order a member list groups people in. */
enum class MemberRole { Admin, Moderator, Member }

/** Someone in a room's member list: joined, or [invited] and yet to answer. */
@Immutable
data class Participant(val member: Member, val role: MemberRole, val invited: Boolean)
