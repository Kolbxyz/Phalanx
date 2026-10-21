package org.phalanx.android.data.model

import androidx.compose.runtime.Immutable

@Immutable
sealed interface SessionState {
    /** The stored session, if any, is still being opened. */
    data object Restoring : SessionState

    data object LoggedOut : SessionState

    data class LoggedIn(val userId: String) : SessionState
}
