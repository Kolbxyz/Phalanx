package org.phalanx.android.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.phalanx.android.R
import org.phalanx.android.data.model.AppSettings
import org.phalanx.android.ui.components.SegmentGap
import org.phalanx.android.ui.components.SegmentedRow
import org.phalanx.android.ui.components.segmentedShape
import org.phalanx.android.ui.theme.Symbols

/** The switch for summaries and the model file they run on, which the user fetches and imports. */
@Composable
internal fun AiSettings(
    current: AppSettings,
    viewModel: SettingsViewModel,
    ai: AiSettingsViewModel = hiltViewModel()
) {
    val model by ai.model.collectAsStateWithLifecycle()
    val import by ai.import.collectAsStateWithLifecycle()
    // A .gguf file has no registered type, so the picker cannot filter on it.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) ai.import(uri)
    }
    Column(verticalArrangement = Arrangement.spacedBy(SegmentGap)) {
        if (!ai.supported) {
            SegmentedRow(
                headline = stringResource(R.string.settings_ai_unsupported),
                shape = segmentedShape(0, 1),
                leading = { Icon(Symbols.Info, contentDescription = null) }
            )
            return@Column
        }
        SwitchGroup(
            listOf(
                SwitchSetting(
                    Symbols.Summarize, R.string.settings_ai_switch, R.string.settings_ai_switch_summary,
                    current.aiSummaries, viewModel::setAiSummaries
                ),
                SwitchSetting(
                    Symbols.SpeechToText, R.string.settings_auto_transcribe, R.string.settings_auto_transcribe_summary,
                    current.autoTranscribe, viewModel::setAutoTranscribe
                ),
                SwitchSetting(
                    Symbols.Lock, R.string.settings_ai_on_disk, R.string.settings_ai_on_disk_summary,
                    current.aiOnDisk, viewModel::setAiOnDisk
                ),
                SwitchSetting(
                    Symbols.PhotoLibrary, R.string.settings_photo_search, R.string.settings_photo_search_summary,
                    current.photoSearch, viewModel::setPhotoSearch
                )
            )
        )
        Spacer(Modifier.height(8.dp))
        val busy = import is ModelImport.Copying
        SegmentedRow(
            headline = stringResource(R.string.settings_ai_model),
            supporting = when (val state = import) {
                is ModelImport.Copying ->
                    stringResource(R.string.settings_ai_model_copying, (state.progress * PERCENT).toInt())
                ModelImport.Rejected -> stringResource(R.string.settings_ai_model_rejected)
                ModelImport.Failed -> stringResource(R.string.settings_ai_model_failed)
                ModelImport.Idle -> model ?: stringResource(R.string.settings_ai_model_none)
            },
            shape = segmentedShape(0, if (model != null) 2 else 1),
            leading = { Icon(Symbols.InsertDriveFile, contentDescription = null) },
            interaction = if (busy) {
                Modifier
            } else {
                Modifier.clickable(role = Role.Button) { picker.launch(arrayOf("*/*")) }
            }
        )
        if (model != null) {
            SegmentedRow(
                headline = stringResource(R.string.settings_ai_model_remove),
                shape = segmentedShape(1, 2),
                leading = { Icon(Symbols.Delete, contentDescription = null) },
                interaction = Modifier.clickable(role = Role.Button, onClick = ai::remove)
            )
        }
        SegmentedRow(
            headline = stringResource(R.string.settings_ai_model_hint_title),
            supporting = stringResource(R.string.settings_ai_model_hint),
            shape = segmentedShape(0, 1),
            leading = { Icon(Symbols.Info, contentDescription = null) }
        )
        SpeechSettingsSection()
        TranslationSettingsSection()
        VisionSettingsSection()
    }
}

private const val PERCENT = 100
