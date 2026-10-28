package org.phalanx.android.ui.screens.room

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.phalanx.android.core.prefs.SettingsStore
import org.phalanx.android.data.model.LinkPreview
import org.phalanx.android.data.model.LinkPreviewSource
import org.phalanx.android.data.repository.LinkPreviewRepository

/** Where one link's preview stands. */
sealed interface LinkPreviewState {
    data object Loading : LinkPreviewState

    data class Ready(val preview: LinkPreview) : LinkPreviewState

    /** The page offers nothing to show, or could not be reached: the message goes without a card. */
    data object None : LinkPreviewState
}

/** The room's link previews, by URL, fetched as their messages come into view. */
@HiltViewModel
class LinkPreviewViewModel @Inject constructor(
    private val previews: LinkPreviewRepository,
    settings: SettingsStore
) : ViewModel() {
    private val source = settings.settings.map { it.linkPreviews }.distinctUntilChanged()

    val enabled: StateFlow<Boolean> = source.map { it != LinkPreviewSource.Off }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val mutableStates = MutableStateFlow<Map<String, LinkPreviewState>>(emptyMap())
    val states: StateFlow<Map<String, LinkPreviewState>> = mutableStates.asStateFlow()

    init {
        // Another source gives other previews, or none: what was fetched before no longer applies.
        viewModelScope.launch { source.collect { mutableStates.value = emptyMap() } }
    }

    fun load(url: String, withImage: Boolean) {
        if (url in mutableStates.value) return
        mutableStates.update { it + (url to LinkPreviewState.Loading) }
        viewModelScope.launch {
            val state = previews.preview(url, withImage).fold(
                onSuccess = { preview -> preview?.let(LinkPreviewState::Ready) ?: LinkPreviewState.None },
                onFailure = { LinkPreviewState.None }
            )
            mutableStates.update { it + (url to state) }
        }
    }
}

/** The room's link previews, for the messages deep in its timeline; null outside a room. */
val LocalLinkPreviews = staticCompositionLocalOf<LinkPreviewViewModel?> { null }
