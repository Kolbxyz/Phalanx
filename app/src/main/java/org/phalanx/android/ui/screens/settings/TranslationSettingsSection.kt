package org.phalanx.android.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import org.phalanx.android.R
import org.phalanx.android.core.ai.ModelTransfer
import org.phalanx.android.core.ai.translation.LanguagePair
import org.phalanx.android.ui.theme.Symbols

/**
 * Offline translation: a card per language pair, each downloaded in one tap, its files together.
 * Each room picks the pair it auto-translates with, in its details.
 */
@Composable
internal fun TranslationModels(viewModel: TranslationSettingsViewModel = hiltViewModel()) {
    val pairs by viewModel.pairs.collectAsStateWithLifecycle()
    val transfer by viewModel.transfer.collectAsStateWithLifecycle()
    // A pair is three files with no useful type, picked together.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.import(uris)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        viewModel.available.forEach { pair ->
            ModelCard(
                title = labelOf(pair),
                detail = stringResource(R.string.settings_translation_engine),
                bytes = viewModel.sizeOf(pair),
                icon = Symbols.Translate,
                installed = pair in pairs,
                busy = transfer is ModelTransfer.Copying,
                onDownload = { viewModel.download(pair) },
                onRemove = { viewModel.remove(pair) }
            )
        }
        TransferStatus(
            transfer,
            R.string.settings_translation_rejected,
            viewModel::cancel,
            onImport = { picker.launch(arrayOf("*/*")) }
        )
    }
}

internal fun labelOf(pair: LanguagePair): String {
    fun name(code: String) = Locale.forLanguageTag(code).let { it.getDisplayLanguage(it) }
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }
    return "${name(pair.from)} → ${name(pair.to)}"
}
