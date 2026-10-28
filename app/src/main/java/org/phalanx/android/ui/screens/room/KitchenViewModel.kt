package org.phalanx.android.ui.screens.room

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.phalanx.android.core.contacts.Conversations
import org.phalanx.android.core.media.EmojiKitchen
import org.phalanx.android.core.media.KitchenStore
import org.phalanx.android.core.prefs.SettingsStore
import org.phalanx.android.data.model.KitchenMix
import org.phalanx.android.data.model.KitchenPicks
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.data.repository.StickerRepository
import org.phalanx.android.ui.navigation.RoomRoute

/** What the composer offers for the emoji typed so far. */
internal sealed interface DraftMixes {
    /** The draft is two emoji that mix: they merge into [mix]. */
    data class Merge(val mix: KitchenMix) : DraftMixes

    /** The draft is one emoji: a few of its mixes, the ones sent lately first. */
    data class Choices(val mixes: List<KitchenMix>) : DraftMixes
}

/**
 * Emoji Kitchen in a room: the sheet, the composer's mixes of typed emoji, and reacting with a
 * mix. Offered only once the user turned it on in Privacy.
 */
@HiltViewModel
internal class KitchenViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    settings: SettingsStore,
    private val kitchen: EmojiKitchen,
    private val store: KitchenStore,
    private val stickers: StickerRepository,
    private val conversations: Conversations
) : ViewModel() {
    private val roomId = savedStateHandle.toRoute<RoomRoute>().roomId

    val enabled: StateFlow<Boolean> = settings.settings.map { it.emojiKitchen }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val picks: StateFlow<KitchenPicks> = store.picks

    init {
        viewModelScope.launch { store.load() }
    }

    /** The bases matching [query] by name (see [EmojiKitchen.search]); all of them when blank. */
    suspend fun bases(query: String): List<String> = kitchen.search(query)

    suspend fun mixesOf(codepoints: String): List<KitchenMix> = kitchen.mixesOf(codepoints)

    /** What to offer for [draft]; null when it is not one or two emoji that mix. */
    suspend fun mixesOfDraft(draft: String): DraftMixes? {
        val emoji = emojiIn(draft) ?: return null
        return when (emoji.size) {
            1 -> {
                val all = kitchen.mixesOfTyped(emoji.single())
                val recent = store.load().recent.filter { it in all }
                (recent + all).distinct().take(MAX_SUGGESTIONS).takeIf { it.isNotEmpty() }?.let(DraftMixes::Choices)
            }
            2 -> kitchen.mixOf(emoji[0], emoji[1])?.let(DraftMixes::Merge)
            else -> null
        }
    }

    /** Null when the image can't be fetched; the cell then shows the two emoji instead. */
    suspend fun image(mix: KitchenMix): File? = kitchen.image(mix).getOrNull()

    fun send(mix: KitchenMix, onFailed: () -> Unit) {
        viewModelScope.launch {
            stickers.send(conversations.targetOf(roomId), mix).fold(
                // Remembering it is a convenience: a failed write only leaves it out of the recent row.
                onSuccess = { store.sent(mix) },
                onFailure = { onFailed() }
            )
        }
    }

    /** Reacts to [message] with [mix]'s image, as an image reaction keyed by its uploaded URL. */
    fun react(message: TimelineEntry.Message, mix: KitchenMix, onFailed: () -> Unit) {
        viewModelScope.launch {
            val session = conversations.open(roomId).getOrNull() ?: return@launch onFailed()
            val url = stickers.upload(mix).getOrElse { return@launch onFailed() }
            session.toggleReaction(message, url).fold(onSuccess = { store.sent(mix) }, onFailure = { onFailed() })
        }
    }

    fun toggleFavourite(mix: KitchenMix, onFailed: () -> Unit) {
        viewModelScope.launch { store.toggleFavourite(mix).onFailure { onFailed() } }
    }

    private companion object {
        const val MAX_SUGGESTIONS = 4
    }
}
