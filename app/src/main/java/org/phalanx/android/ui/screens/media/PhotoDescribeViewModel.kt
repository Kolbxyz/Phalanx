package org.phalanx.android.ui.screens.media

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.phalanx.android.R
import org.phalanx.android.core.ai.vision.PhotoDescriber
import org.phalanx.android.core.ai.vision.PhotoWords
import org.phalanx.android.core.ai.vision.UndecodableImageException
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.repository.MediaRepository

/** Where describing the photo on screen stands. */
sealed interface DescribeState {
    data object Working : DescribeState

    data class Done(val words: PhotoWords) : DescribeState

    data class Failed(@StringRes val message: Int) : DescribeState
}

/** Describes the photo on screen with the on-device vision model, on request. */
@HiltViewModel
class PhotoDescribeViewModel @Inject constructor(
    private val media: MediaRepository,
    private val describer: PhotoDescriber
) : ViewModel() {
    /** Whether both models are imported, so describing is offered at all. */
    val ready: StateFlow<Boolean> = describer.ready.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val mutableState = MutableStateFlow<DescribeState?>(null)

    /** Null while no description is asked for. */
    val state: StateFlow<DescribeState?> = mutableState.asStateFlow()

    private var job: Job? = null

    fun describe(image: MessageBody.Image) {
        job?.cancel()
        mutableState.value = DescribeState.Working
        job = viewModelScope.launch {
            val file = media.openable(image.media, image.filename, image.mimeType).getOrElse {
                mutableState.value = DescribeState.Failed(R.string.describe_download_failed)
                return@launch
            }
            mutableState.value = describer.describe(file).fold(
                onSuccess = { DescribeState.Done(it) },
                onFailure = {
                    DescribeState.Failed(
                        if (it is UndecodableImageException) R.string.describe_unreadable else R.string.describe_failed
                    )
                }
            )
        }
    }

    /** Closes the description, stopping the model if it is still writing. */
    fun dismiss() {
        job?.cancel()
        mutableState.value = null
    }
}
