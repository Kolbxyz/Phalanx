package org.phalanx.android.ui.screens.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.phalanx.android.core.ai.LlamaNative
import org.phalanx.android.core.ai.ModelRejectedException
import org.phalanx.android.core.ai.ModelStore

sealed interface ModelImport {
    data object Idle : ModelImport
    data class Copying(val progress: Float) : ModelImport
    data object Rejected : ModelImport
    data object Failed : ModelImport
}

@HiltViewModel
class AiSettingsViewModel @Inject constructor(private val store: ModelStore) : ViewModel() {
    /** False on a phone without a 64-bit processor, where the native library is not shipped. */
    val supported: Boolean = LlamaNative.isAvailable

    /** The name of the imported model, or null. */
    val model: StateFlow<String?> = store.installed

    private val mutableImport = MutableStateFlow<ModelImport>(ModelImport.Idle)
    val import: StateFlow<ModelImport> = mutableImport.asStateFlow()

    fun import(uri: Uri) {
        if (mutableImport.value is ModelImport.Copying) return
        mutableImport.value = ModelImport.Copying(0f)
        viewModelScope.launch {
            store.import(uri) { mutableImport.value = ModelImport.Copying(it) }.fold(
                onSuccess = { mutableImport.value = ModelImport.Idle },
                onFailure = {
                    mutableImport.value = if (it is ModelRejectedException) ModelImport.Rejected else ModelImport.Failed
                }
            )
        }
    }

    fun remove() {
        viewModelScope.launch { store.remove() }
    }
}
