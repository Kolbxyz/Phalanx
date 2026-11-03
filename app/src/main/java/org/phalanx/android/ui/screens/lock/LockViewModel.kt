package org.phalanx.android.ui.screens.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.phalanx.android.core.security.DuressCode
import org.phalanx.android.core.security.DuressWipe
import org.phalanx.android.di.ApplicationScope

/** What the code field on the lock screen shows. */
internal enum class CodeCheck { Idle, Checking, Wrong }

/**
 * The lock screen's code field, offered once a duress code is set. Any other code is turned down
 * like a mistyped one; the duress code starts [DuressWipe] and the field keeps checking until the
 * process ends.
 */
@HiltViewModel
internal class LockViewModel @Inject constructor(
    private val duress: DuressCode,
    private val wipe: DuressWipe,
    @ApplicationScope private val appScope: CoroutineScope
) : ViewModel() {
    val offersCode: StateFlow<Boolean> = duress.isSet

    private val mutableCheck = MutableStateFlow(CodeCheck.Idle)
    val check: StateFlow<CodeCheck> = mutableCheck.asStateFlow()

    fun submit(code: String) {
        if (mutableCheck.value == CodeCheck.Checking || code.isEmpty()) return
        mutableCheck.value = CodeCheck.Checking
        viewModelScope.launch {
            if (duress.matches(code)) {
                // Outlives this screen: nothing the user does next may stop it halfway.
                appScope.launch { wipe.wipe() }
            } else {
                mutableCheck.value = CodeCheck.Wrong
            }
        }
    }

    /** The user is typing again after a wrong code. */
    fun edited() {
        if (mutableCheck.value == CodeCheck.Wrong) mutableCheck.value = CodeCheck.Idle
    }
}
