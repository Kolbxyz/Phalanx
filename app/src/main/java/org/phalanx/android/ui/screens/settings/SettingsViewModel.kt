package org.phalanx.android.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.phalanx.android.core.prefs.SettingsStore
import org.phalanx.android.data.model.AppSettings
import org.phalanx.android.data.model.TextSize
import org.phalanx.android.data.model.ThemeMode
import org.phalanx.android.data.repository.BlockedUsersRepository
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: SettingsStore,
    private val blockedUsers: BlockedUsersRepository
) : ViewModel() {
    /** Null until the file has been read once, so switches never flash their defaults. */
    val settings: StateFlow<AppSettings?> = store.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** Null until the account's list has loaded. */
    val blocked: StateFlow<List<String>?> = blockedUsers.blocked
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** Set when an unblock failed; cleared once shown. */
    private val mutableFailed = MutableStateFlow(false)
    val unblockFailed: StateFlow<Boolean> = mutableFailed.asStateFlow()

    fun setThemeMode(mode: ThemeMode) = edit { store.setThemeMode(mode) }

    fun setDynamicColor(enabled: Boolean) = edit { store.setDynamicColor(enabled) }

    fun setAmoledBlack(enabled: Boolean) = edit { store.setAmoledBlack(enabled) }

    fun setReduceMotion(enabled: Boolean) = edit { store.setReduceMotion(enabled) }

    fun setMessageTextSize(size: TextSize) = edit { store.setMessageTextSize(size) }

    fun setEnterToSend(enabled: Boolean) = edit { store.setEnterToSend(enabled) }

    fun setMarkdown(enabled: Boolean) = edit { store.setMarkdown(enabled) }

    fun setFullQualityImages(enabled: Boolean) = edit { store.setFullQualityImages(enabled) }

    fun setAutoLoadMedia(enabled: Boolean) = edit { store.setAutoLoadMedia(enabled) }

    fun setPublicReadReceipts(enabled: Boolean) = edit { store.setPublicReadReceipts(enabled) }

    fun unblock(userId: String) = edit {
        blockedUsers.unblock(userId).onFailure { mutableFailed.value = true }
    }

    fun dismissFailure() {
        mutableFailed.value = false
    }

    private fun edit(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
