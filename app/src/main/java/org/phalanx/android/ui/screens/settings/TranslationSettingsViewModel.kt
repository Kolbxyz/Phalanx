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
import org.phalanx.android.core.ai.ModelRejectedException
import org.phalanx.android.core.ai.translation.LanguagePair
import org.phalanx.android.core.ai.translation.TranslationModelStore

/** The offline translation pairs: which are installed, importing their files, removing one. */
@HiltViewModel
class TranslationSettingsViewModel @Inject constructor(private val store: TranslationModelStore) : ViewModel() {
    val pairs: StateFlow<List<LanguagePair>> = store.pairs

    private val mutableImport = MutableStateFlow<ModelImport>(ModelImport.Idle)
    val import: StateFlow<ModelImport> = mutableImport.asStateFlow()

    /** Copies [uris] in one after the other, a pair's files in any order; stops at the first refused. */
    fun import(uris: List<Uri>) {
        if (mutableImport.value is ModelImport.Copying || uris.isEmpty()) return
        mutableImport.value = ModelImport.Copying(0f)
        viewModelScope.launch {
            var outcome: ModelImport = ModelImport.Idle
            for ((index, uri) in uris.withIndex()) {
                val result = store.import(uri) { mutableImport.value = ModelImport.Copying((index + it) / uris.size) }
                result.exceptionOrNull()?.let {
                    outcome = if (it is ModelRejectedException) ModelImport.Rejected else ModelImport.Failed
                }
                if (outcome != ModelImport.Idle) break
            }
            mutableImport.value = outcome
        }
    }

    fun remove(pair: LanguagePair) {
        viewModelScope.launch { store.remove(pair) }
    }
}
