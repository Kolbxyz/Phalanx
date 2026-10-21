package org.phalanx.android.ui.screens.media

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.data.repository.TimelineRepository
import org.phalanx.android.ui.navigation.MediaViewerRoute
import javax.inject.Inject

/**
 * Finds the image in its room's (cached) timeline. The route only names the room and the item,
 * never the media itself: navigation state can be saved to disk, and media references carry keys.
 */
@HiltViewModel
class MediaViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    timelines: TimelineRepository
) : ViewModel() {
    val entryId: String = savedStateHandle.toRoute<MediaViewerRoute>().entryId

    private val mutableImage = MutableStateFlow<MessageBody.Image?>(null)
    val image: StateFlow<MessageBody.Image?> = mutableImage.asStateFlow()

    init {
        val roomId = savedStateHandle.toRoute<MediaViewerRoute>().roomId
        viewModelScope.launch {
            val session = timelines.open(roomId).getOrNull() ?: return@launch
            session.entries.collect { entries ->
                val message = entries.firstOrNull { it.id == entryId } as? TimelineEntry.Message
                mutableImage.value = message?.body as? MessageBody.Image
            }
        }
    }
}
