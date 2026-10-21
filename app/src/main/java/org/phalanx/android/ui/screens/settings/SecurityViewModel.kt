package org.phalanx.android.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.phalanx.android.data.repository.SecurityRepository
import org.phalanx.android.data.repository.SecurityStatus
import org.phalanx.android.data.repository.VerificationStep
import javax.inject.Inject

/** What the security page shows besides the SDK's own state: dialogs and work in progress. */
data class SecurityUi(
    val busy: Boolean = false,
    /** A recovery key to show once, right after it was created or changed. */
    val newKey: String? = null,
    val entering: Boolean = false,
    val keyRejected: Boolean = false,
    val failed: Boolean = false
)

@HiltViewModel
class SecurityViewModel @Inject constructor(private val security: SecurityRepository) : ViewModel() {
    val status: StateFlow<SecurityStatus?> = security.status
    val verification: StateFlow<VerificationStep> = security.verification

    private val mutableUi = MutableStateFlow(SecurityUi())
    val ui: StateFlow<SecurityUi> = mutableUi.asStateFlow()

    fun setUpRecovery() = createKey { security.setUpRecovery() }

    fun changeRecoveryKey() = createKey { security.changeRecoveryKey() }

    fun dismissKey() = mutableUi.update { it.copy(newKey = null) }

    fun startEntering() = mutableUi.update { it.copy(entering = true, keyRejected = false) }

    fun dismissEntering() = mutableUi.update { it.copy(entering = false, keyRejected = false) }

    fun enterKey(key: String) {
        mutableUi.update { it.copy(busy = true, keyRejected = false) }
        viewModelScope.launch {
            security.enterRecoveryKey(key).fold(
                onSuccess = { mutableUi.update { it.copy(busy = false, entering = false) } },
                onFailure = { mutableUi.update { it.copy(busy = false, keyRejected = true) } }
            )
        }
    }

    fun startVerification() {
        viewModelScope.launch { security.startVerification() }
    }

    fun confirmVerification() {
        viewModelScope.launch { security.confirmVerification() }
    }

    fun cancelVerification() {
        viewModelScope.launch { security.cancelVerification() }
    }

    fun closeVerification() = security.resetVerification()

    fun dismissFailure() = mutableUi.update { it.copy(failed = false) }

    private fun createKey(block: suspend () -> Result<String>) {
        mutableUi.update { it.copy(busy = true) }
        viewModelScope.launch {
            block().fold(
                onSuccess = { key -> mutableUi.update { it.copy(busy = false, newKey = key) } },
                onFailure = { mutableUi.update { it.copy(busy = false, failed = true) } }
            )
        }
    }
}
