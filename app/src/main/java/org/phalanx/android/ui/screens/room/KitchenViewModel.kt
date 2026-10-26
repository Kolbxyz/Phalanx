package org.phalanx.android.ui.screens.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.phalanx.android.core.media.EmojiKitchen
import org.phalanx.android.core.prefs.SettingsStore
import org.phalanx.android.data.model.KitchenMix

/**
 * The Emoji Kitchen sheet and the composer's mix of two typed emoji, offered only once the user
 * turned it on in Privacy.
 */
@HiltViewModel
class KitchenViewModel @Inject constructor(
    settings: SettingsStore,
    private val kitchen: EmojiKitchen
) : ViewModel() {
    val enabled: StateFlow<Boolean> = settings.settings.map { it.emojiKitchen }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    suspend fun bases(): List<String> = kitchen.bases()

    suspend fun mixesOf(codepoints: String): List<KitchenMix> = kitchen.mixesOf(codepoints)

    /** The mix of the draft's emoji when it is exactly two that mix; null otherwise. */
    suspend fun mixOfDraft(draft: String): KitchenMix? {
        val (first, second) = emojiIn(draft)?.takeIf { it.size == 2 } ?: return null
        return kitchen.mixOf(first, second)
    }

    /** Null when the image can't be fetched; the cell then shows the two emoji instead. */
    suspend fun image(mix: KitchenMix): File? = kitchen.image(mix).getOrNull()
}
