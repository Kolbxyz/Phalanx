package org.phalanx.android.ui.screens.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.phalanx.android.core.ai.translation.LanguagePair
import org.phalanx.android.core.ai.translation.TranslationModelStore
import org.phalanx.android.core.prefs.RoomPrefsStore
import org.phalanx.android.ui.navigation.RoomDetailsRoute

/** The installed translation pairs, and which one this room auto-translates with, if any. */
data class RoomTranslateState(val installed: List<LanguagePair>, val chosen: LanguagePair?)

@HiltViewModel
class RoomTranslateViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val store: TranslationModelStore,
    private val roomPrefs: RoomPrefsStore
) : ViewModel() {
    private val roomId = savedStateHandle.toRoute<RoomDetailsRoute>().roomId

    val state: StateFlow<RoomTranslateState> = combine(store.pairs, roomPrefs.prefs) { installed, prefs ->
        val chosen = prefs?.get(roomId)?.translatePair?.let(LanguagePair::ofKey)?.takeIf { it in installed }
        RoomTranslateState(installed, chosen)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, RoomTranslateState(store.pairs.value, null))

    private val mutableFailed = MutableStateFlow(false)

    /** The last choice could not be saved. */
    val failed: StateFlow<Boolean> = mutableFailed.asStateFlow()

    /** On picks the installed pair into the phone's language, or the first one; off clears it. */
    fun setEnabled(enabled: Boolean) {
        val installed = store.pairs.value
        val pair = installed.firstOrNull { it.to == Locale.getDefault().language } ?: installed.firstOrNull()
        choose(pair.takeIf { enabled })
    }

    fun choose(pair: LanguagePair?) {
        viewModelScope.launch {
            mutableFailed.value = try {
                roomPrefs.update(roomId) { it.copy(translatePair = pair?.key) }
                false
            } catch (_: IOException) {
                true
            }
        }
    }
}
