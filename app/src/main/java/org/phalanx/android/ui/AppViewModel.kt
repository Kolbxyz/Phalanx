package org.phalanx.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.phalanx.android.data.model.SessionState
import org.phalanx.android.data.repository.AuthRepository
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(auth: AuthRepository) : ViewModel() {
    val sessionState: StateFlow<SessionState> = auth.sessionState

    init {
        viewModelScope.launch { auth.restoreSession() }
    }
}
