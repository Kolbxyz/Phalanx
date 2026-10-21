package org.phalanx.android.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.phalanx.android.core.prefs.SettingsStore
import org.phalanx.android.data.model.AppSettings
import org.phalanx.android.data.model.ThemeMode
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(private val store: SettingsStore) : ViewModel() {
    /** Null until the file has been read once, so switches never flash their defaults. */
    val settings: StateFlow<AppSettings?> = store.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    fun setThemeMode(mode: ThemeMode) = edit { store.setThemeMode(mode) }

    fun setDynamicColor(enabled: Boolean) = edit { store.setDynamicColor(enabled) }

    fun setAmoledBlack(enabled: Boolean) = edit { store.setAmoledBlack(enabled) }

    fun setReduceMotion(enabled: Boolean) = edit { store.setReduceMotion(enabled) }

    fun setPublicReadReceipts(enabled: Boolean) = edit { store.setPublicReadReceipts(enabled) }

    private fun edit(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
