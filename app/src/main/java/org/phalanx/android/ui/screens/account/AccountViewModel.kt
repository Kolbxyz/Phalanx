package org.phalanx.android.ui.screens.account

import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.phalanx.android.core.media.AvatarEncoder
import org.phalanx.android.data.model.AccountException
import org.phalanx.android.data.model.AccountFailure
import org.phalanx.android.data.repository.AccountPage
import org.phalanx.android.data.repository.AccountProfile
import org.phalanx.android.data.repository.AccountRepository
import org.phalanx.android.ui.components.decodeAvatar

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val account: AccountRepository,
    private val encoder: AvatarEncoder
) : ViewModel() {
    private val mutableState = MutableStateFlow(AccountUiState())
    val state: StateFlow<AccountUiState> = mutableState.asStateFlow()

    val profile: StateFlow<AccountProfile?> = account.profile

    val avatar: StateFlow<ImageBitmap?> = account.avatar(AVATAR_PX)
        .map(::decodeAvatar)
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    init {
        viewModelScope.launch {
            val details = account.details()
            mutableState.update { it.copy(details = details) }
        }
    }

    /** Only once the profile is known, so the dialog never opens empty over a name that exists. */
    fun editName() {
        val current = profile.value ?: return
        mutableState.update { it.copy(nameDraft = current.displayName.orEmpty(), nameFailed = false) }
    }

    fun onNameChange(value: String) = mutableState.update { it.copy(nameDraft = value, nameFailed = false) }

    fun dismissName() = mutableState.update { it.copy(nameDraft = null, nameFailed = false) }

    fun saveName() {
        val draft = mutableState.value.nameDraft?.takeIf { it.isNotBlank() } ?: return
        mutableState.update { it.copy(savingName = true, nameFailed = false) }
        viewModelScope.launch {
            account.setDisplayName(draft).fold(
                // The profile listener brings the new name back, so only the dialog needs closing.
                onSuccess = { mutableState.update { it.copy(savingName = false, nameDraft = null) } },
                onFailure = { mutableState.update { it.copy(savingName = false, nameFailed = true) } }
            )
        }
    }

    /** [uri] from the photo picker; re-encoded before upload, see [AvatarEncoder]. */
    fun setAvatar(uri: Uri) = avatarAction {
        val jpeg = try {
            encoder.encode(uri)
        } catch (e: IOException) {
            return@avatarAction Result.failure(e)
        } catch (e: SecurityException) {
            // The picker's grant expired, or the file came from an app that revoked it.
            return@avatarAction Result.failure(e)
        }
        account.setAvatar(jpeg)
    }

    fun removeAvatar() = avatarAction { account.removeAvatar() }

    private fun avatarAction(block: suspend () -> Result<Unit>) {
        if (mutableState.value.avatarBusy) return
        mutableState.update { it.copy(avatarBusy = true, avatarFailed = false) }
        viewModelScope.launch {
            val failed = block().isFailure
            mutableState.update { it.copy(avatarBusy = false, avatarFailed = failed) }
        }
    }

    fun startDeactivation() {
        // Provider-managed accounts are deactivated on the provider's site, which owns the password.
        if (mutableState.value.details?.canDeactivate == true) {
            mutableState.update { it.copy(deactivation = DeactivationDraft()) }
        } else {
            openManage(AccountPage.Deactivate)
        }
    }

    fun onDeactivationPassword(value: String) = updateDeactivation {
        it.copy(password = value, wrongPassword = false, failed = false)
    }

    fun onDeactivationErase(value: Boolean) = updateDeactivation { it.copy(erase = value) }

    fun dismissDeactivation() {
        if (mutableState.value.deactivation?.busy == true) return
        mutableState.update { it.copy(deactivation = null) }
    }

    fun deactivate() {
        val draft = mutableState.value.deactivation?.takeIf { it.canConfirm } ?: return
        updateDeactivation { it.copy(busy = true, wrongPassword = false, failed = false) }
        // Not cancellable, like sign-out: on success the account is gone and must be forgotten here.
        viewModelScope.launch {
            val result = withContext(NonCancellable) { account.deactivate(draft.password, draft.erase) }
            result.onFailure { error ->
                val wrong = (error as? AccountException)?.failure == AccountFailure.WrongPassword
                updateDeactivation { it.copy(busy = false, wrongPassword = wrong, failed = !wrong) }
            }
        }
    }

    private fun updateDeactivation(change: (DeactivationDraft) -> DeactivationDraft) = mutableState.update {
        it.copy(deactivation = it.deactivation?.let(change))
    }

    fun openManage(page: AccountPage) {
        viewModelScope.launch {
            val url = account.manageUrl(page) ?: return@launch
            mutableState.update { it.copy(browserUrl = url) }
        }
    }

    fun onBrowserOpened() = mutableState.update { it.copy(browserUrl = null) }

    fun askSignOut() = mutableState.update { it.copy(confirmingSignOut = true) }

    fun dismissSignOut() = mutableState.update { it.copy(confirmingSignOut = false) }

    fun signOut() {
        mutableState.update { it.copy(signingOut = true) }
        // The session state flips to LoggedOut, which replaces the whole navigation graph. Not
        // cancellable: leaving the screen mid-way must not leave a half-deleted account behind.
        viewModelScope.launch { withContext(NonCancellable) { account.signOut() } }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        // The 96 dp header avatar at up to 3x density.
        const val AVATAR_PX = 288
    }
}
