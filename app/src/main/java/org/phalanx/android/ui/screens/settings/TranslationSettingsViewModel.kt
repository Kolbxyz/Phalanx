package org.phalanx.android.ui.screens.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.phalanx.android.core.ai.ModelTransfer
import org.phalanx.android.core.ai.translation.LanguagePair
import org.phalanx.android.core.ai.translation.TranslationModelStore

/** The offline translation pairs: which are installed, downloading or importing one, removing one. */
@HiltViewModel
class TranslationSettingsViewModel @Inject constructor(private val store: TranslationModelStore) : ViewModel() {
    /** Every pair it can download. */
    val available: List<LanguagePair> = store.available

    val pairs: StateFlow<List<LanguagePair>> = store.pairs

    /** The download or import under way, or how the last one ended. It goes on outside this screen. */
    val transfer: StateFlow<ModelTransfer> = store.transfer

    fun sizeOf(pair: LanguagePair): Long = store.sizeOf(pair)

    fun download(pair: LanguagePair) = store.download(pair)

    fun import(uris: List<Uri>) = store.import(uris)

    fun cancel() = store.cancel()

    fun remove(pair: LanguagePair) {
        viewModelScope.launch { store.remove(pair) }
    }
}
