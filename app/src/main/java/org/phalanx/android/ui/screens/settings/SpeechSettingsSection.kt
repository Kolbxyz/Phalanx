package org.phalanx.android.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.phalanx.android.R
import org.phalanx.android.ui.components.SegmentedRow
import org.phalanx.android.ui.components.SegmentedSection
import org.phalanx.android.ui.components.segmentedShape
import org.phalanx.android.ui.theme.Symbols

/** The Whisper model that writes voice messages out, which the user fetches and imports. */
@Composable
internal fun SpeechSettingsSection(viewModel: SpeechSettingsViewModel = hiltViewModel()) {
    val model by viewModel.model.collectAsStateWithLifecycle()
    val import by viewModel.import.collectAsStateWithLifecycle()
    // A .bin file has no useful type, so the picker cannot filter on it.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.import(uri)
    }
    SegmentedSection(stringResource(R.string.settings_speech_section)) {
        val busy = import is ModelImport.Copying
        val rows = if (model != null) 3 else 2
        SegmentedRow(
            headline = stringResource(R.string.settings_speech_model),
            supporting = when (val state = import) {
                is ModelImport.Copying ->
                    stringResource(R.string.settings_ai_model_copying, (state.progress * PERCENT).toInt())
                ModelImport.Rejected -> stringResource(R.string.settings_speech_model_rejected)
                ModelImport.Failed -> stringResource(R.string.settings_ai_model_failed)
                ModelImport.Idle -> model ?: stringResource(R.string.settings_ai_model_none)
            },
            shape = segmentedShape(0, rows),
            leading = { Icon(Symbols.SpeechToText, contentDescription = null) },
            interaction = if (busy) {
                Modifier
            } else {
                Modifier.clickable(role = Role.Button) { picker.launch(arrayOf("*/*")) }
            }
        )
        if (model != null) {
            SegmentedRow(
                headline = stringResource(R.string.settings_ai_model_remove),
                shape = segmentedShape(1, rows),
                leading = { Icon(Symbols.Delete, contentDescription = null) },
                interaction = Modifier.clickable(role = Role.Button, onClick = viewModel::remove)
            )
        }
        SegmentedRow(
            headline = stringResource(R.string.settings_speech_hint_title),
            supporting = stringResource(R.string.settings_speech_hint),
            shape = segmentedShape(rows - 1, rows),
            leading = { Icon(Symbols.Info, contentDescription = null) }
        )
    }
}

private const val PERCENT = 100
