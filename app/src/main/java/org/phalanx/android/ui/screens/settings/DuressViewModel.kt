package org.phalanx.android.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.security.GeneralSecurityException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.phalanx.android.core.security.DuressCode

/** Setting and removing the duress code, from the screen privacy settings. */
@HiltViewModel
internal class DuressViewModel @Inject constructor(private val duress: DuressCode) : ViewModel() {
    val isSet: StateFlow<Boolean> = duress.isSet

    private val mutableFailed = MutableStateFlow(false)

    /** The last save could not be written; the dialog stays open to say so. */
    val failed: StateFlow<Boolean> = mutableFailed.asStateFlow()

    fun save(code: String, onSaved: () -> Unit) {
        viewModelScope.launch {
            try {
                duress.set(code)
                mutableFailed.value = false
                onSaved()
            } catch (_: IOException) {
                mutableFailed.value = true
            } catch (_: GeneralSecurityException) {
                mutableFailed.value = true
            }
        }
    }

    fun remove() {
        viewModelScope.launch { duress.clear() }
    }

    fun dismissed() {
        mutableFailed.value = false
    }
}
