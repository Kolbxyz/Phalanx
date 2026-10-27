package org.phalanx.android.ui.screens.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.phalanx.android.core.ai.KNOWN_MODELS
import org.phalanx.android.core.ai.KnownModel
import org.phalanx.android.core.ai.LlamaNative
import org.phalanx.android.core.ai.ModelStore
import org.phalanx.android.core.ai.ModelTransfer

/** The language model every AI feature runs on: which one is installed, and getting another. */
@HiltViewModel
class AiSettingsViewModel @Inject constructor(private val store: ModelStore) : ViewModel() {
    /** False on a phone without a 64-bit processor, where the native library is not shipped. */
    val supported: Boolean = LlamaNative.isAvailable

    /** The models it can download, one of which may be installed. */
    val models: List<KnownModel> = KNOWN_MODELS

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
