package org.phalanx.android.ui.screens.account

import androidx.compose.runtime.Immutable
import org.phalanx.android.data.repository.AccountDetails

@Immutable
data class AccountUiState(
    val details: AccountDetails? = null,
    /** Non-null while the display-name dialog is open: what the field holds. */
    val nameDraft: String? = null,
    val savingName: Boolean = false,
    val nameFailed: Boolean = false,
    val avatarBusy: Boolean = false,
    val avatarFailed: Boolean = false,
    val confirmingSignOut: Boolean = false,
    val signingOut: Boolean = false,
    /** Non-null while the deactivation dialog is open. */
    val deactivation: DeactivationDraft? = null,
    /** A provider page the screen should open next; cleared once it is open. */
    val browserUrl: String? = null
)

@Immutable
data class DeactivationDraft(
    val password: String = "",
    val erase: Boolean = false,
    val busy: Boolean = false,
    val wrongPassword: Boolean = false,
    val failed: Boolean = false
) {
    val canConfirm: Boolean get() = password.isNotEmpty() && !busy
}
