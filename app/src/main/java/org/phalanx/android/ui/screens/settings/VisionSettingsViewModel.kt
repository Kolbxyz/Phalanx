package org.phalanx.android.ui.screens.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.phalanx.android.core.ai.KnownModel
import org.phalanx.android.core.ai.ModelTransfer
import org.phalanx.android.core.ai.vision.KNOWN_VISION_MODELS
import org.phalanx.android.core.ai.vision.VisionModelStore

/** The vision projector photo search runs on: whether it is installed, and getting it. */
@HiltViewModel
class VisionSettingsViewModel @Inject constructor(private val store: VisionModelStore) : ViewModel() {
    /** The models it can download, one of which may be installed. */
    val models: List<KnownModel> = KNOWN_VISION_MODELS

    /** The name of the installed model, or null. */
    val model: StateFlow<String?> = store.installed

    /** The download or import under way, or how the last one ended. It goes on outside this screen. */
    val transfer: StateFlow<ModelTransfer> = store.transfer

    fun download(model: KnownModel) = store.download(model)

    fun import(uri: Uri) = store.import(uri)

    fun cancel() = store.cancel()

    fun remove() {
        viewModelScope.launch { store.remove() }
    }
}
