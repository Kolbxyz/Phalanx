package org.phalanx.android.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import org.phalanx.android.R
import org.phalanx.android.core.ai.ModelTransfer
import org.phalanx.android.core.ai.translation.LanguagePair
import org.phalanx.android.ui.components.SegmentedRow
import org.phalanx.android.ui.components.SegmentedSection
import org.phalanx.android.ui.components.segmentedShape
import org.phalanx.android.ui.theme.Symbols

/** Offline translation: a card per language pair, each downloaded in one tap, its files together. */
@Composable
internal fun TranslationSettingsSection(viewModel: TranslationSettingsViewModel = hiltViewModel()) {
    val pairs by viewModel.pairs.collectAsStateWithLifecycle()
    val transfer by viewModel.transfer.collectAsStateWithLifecycle()
    // A pair is three files with no useful type, picked together.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.import(uris)
    }
    SegmentedSection(stringResource(R.string.settings_translation_section)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            viewModel.available.forEach { pair ->
                ModelCard(
                    title = labelOf(pair),
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
        SegmentedRow(
            headline = stringResource(R.string.settings_translation_hint_title),
            supporting = stringResource(R.string.settings_translation_hint),
            shape = segmentedShape(0, 1),
            leading = { Icon(Symbols.Info, contentDescription = null) }
        )
    }
}

private fun labelOf(pair: LanguagePair): String {
    fun name(code: String) = Locale.forLanguageTag(code).let { it.getDisplayLanguage(it) }
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }
    return "${name(pair.from)} → ${name(pair.to)}"
}
