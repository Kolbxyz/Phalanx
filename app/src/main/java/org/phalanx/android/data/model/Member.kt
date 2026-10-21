package org.phalanx.android.data.model

import androidx.compose.runtime.Immutable

/** A room member as shown in a list: [name] is their display name, or their Matrix ID without one. */
@Immutable
data class Member(val userId: String, val name: String, val avatar: MediaRef?)
