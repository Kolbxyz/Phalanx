package org.phalanx.android.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import org.phalanx.android.R
import org.phalanx.android.core.ai.translation.LanguagePair
import org.phalanx.android.ui.components.SegmentedRow
import org.phalanx.android.ui.components.SegmentedSection
import org.phalanx.android.ui.components.segmentedShape
import org.phalanx.android.ui.theme.Symbols

/** Offline translation: the language pairs imported, each removable, and importing more files. */
@Composable
internal fun TranslationSettingsSection(viewModel: TranslationSettingsViewModel = hiltViewModel()) {
    val pairs by viewModel.pairs.collectAsStateWithLifecycle()
    val import by viewModel.import.collectAsStateWithLifecycle()
    // A pair is three or four files with no useful type, picked together.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.import(uris)
    }
    SegmentedSection(stringResource(R.string.settings_translation_section)) {
        val rows = pairs.size + 2
        pairs.forEachIndexed { index, pair ->
            SegmentedRow(
                headline = labelOf(pair),
                shape = segmentedShape(index, rows),
                leading = { Icon(Symbols.Translate, contentDescription = null) },
                trailing = {
                    IconButton(onClick = { viewModel.remove(pair) }) {
                        Icon(Symbols.Delete, contentDescription = stringResource(R.string.settings_ai_model_remove))
                    }
                }
            )
        }
        val busy = import is ModelImport.Copying
        SegmentedRow(
            headline = stringResource(R.string.settings_translation_import),
            supporting = when (val state = import) {
                is ModelImport.Copying ->
                    stringResource(R.string.settings_ai_model_copying, (state.progress * PERCENT).toInt())
                ModelImport.Rejected -> stringResource(R.string.settings_translation_rejected)
                ModelImport.Failed -> stringResource(R.string.settings_ai_model_failed)
                ModelImport.Idle -> null
            },
            shape = segmentedShape(pairs.size, rows),
            leading = { Icon(Symbols.InsertDriveFile, contentDescription = null) },
            interaction = if (busy) {
                Modifier
            } else {
                Modifier.clickable(role = Role.Button) { picker.launch(arrayOf("*/*")) }
            }
        )
        SegmentedRow(
            headline = stringResource(R.string.settings_translation_hint_title),
            supporting = stringResource(R.string.settings_translation_hint),
            shape = segmentedShape(rows - 1, rows),
            leading = { Icon(Symbols.Info, contentDescription = null) }
        )
    }
}

private fun labelOf(pair: LanguagePair): String {
    fun name(code: String) = Locale.forLanguageTag(code).let { it.getDisplayLanguage(it) }
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }
    return "${name(pair.from)} → ${name(pair.to)}"
}

private const val PERCENT = 100
