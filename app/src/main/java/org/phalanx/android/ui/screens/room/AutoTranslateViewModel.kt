package org.phalanx.android.ui.screens.room

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.phalanx.android.core.ai.translation.LanguagePair
import org.phalanx.android.core.ai.translation.Translator
import org.phalanx.android.core.prefs.RoomPrefsStore
import org.phalanx.android.ui.navigation.RoomRoute

/**
 * The room's messages translated as they come into view, the user's own included, with the pair
 * chosen for this room in its details. Only Bergamot runs, about a second each; the translations
 * stay in memory.
 */
@HiltViewModel
class AutoTranslateViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val translator: Translator,
    roomPrefs: RoomPrefsStore
) : ViewModel() {
    private val roomId = savedStateHandle.toRoute<RoomRoute>().roomId

    /** The room's pair once it is installed; null leaves every message as it is. */
    val pair: StateFlow<LanguagePair?> = combine(roomPrefs.prefs, translator.pairs) { prefs, installed ->
        prefs?.get(roomId)?.translatePair?.let(LanguagePair::ofKey)?.takeIf { it in installed }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val mutableOriginals = MutableStateFlow<Set<String>>(emptySet())

    /** The messages the user tapped to read as they were written. */
    val originals: StateFlow<Set<String>> = mutableOriginals.asStateFlow()

    /** Shows [key] as written, or its translation again. */
    fun toggleOriginal(key: String) = mutableOriginals.update { if (key in it) it - key else it + key }

    private val mutableTranslations = MutableStateFlow<Map<String, String>>(emptyMap())

    /** By message, the translations done so far. */
    val translations: StateFlow<Map<String, String>> = mutableTranslations.asStateFlow()

    // Messages already looked at with a pair, translated or not, so scrolling back runs nothing again.
    private val seen = HashSet<Pair<LanguagePair, String>>()

    init {
        // Another pair, or none: the translations so far no longer apply.
        viewModelScope.launch {
            pair.collect {
                seen.clear()
                mutableTranslations.value = emptyMap()
                mutableOriginals.value = emptySet()
            }
        }
    }

    /** Translates [text], known as [key], if it reads as the chosen pair's source language. */
    fun translateOnSight(key: String, text: String) {
        val chosen = pair.value ?: return
        if (!seen.add(chosen to key)) return
        // Bergamot is fast but not free: scrolling a long room translates only what is seen.
        viewModelScope.launch {
            translator.translateWith(chosen, text)?.onSuccess { translated ->
                mutableTranslations.update { it + (key to translated) }
            }
        }
    }
}

/** The room's auto-translations, for the messages deep in its timeline; null outside a room. */
val LocalAutoTranslations = staticCompositionLocalOf<AutoTranslateViewModel?> { null }
