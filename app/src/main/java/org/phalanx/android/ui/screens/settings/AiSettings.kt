package org.phalanx.android.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.phalanx.android.R
import org.phalanx.android.data.model.AppSettings
import org.phalanx.android.ui.components.SegmentGap
import org.phalanx.android.ui.components.SegmentedRow
import org.phalanx.android.ui.components.segmentedShape
import org.phalanx.android.ui.theme.Symbols

/** The AI switches and the language model they run on, downloaded in one tap. */
@Composable
internal fun AiSettings(
    current: AppSettings,
    viewModel: SettingsViewModel,
    ai: AiSettingsViewModel = hiltViewModel()
) {
    val model by ai.model.collectAsStateWithLifecycle()
    val transfer by ai.transfer.collectAsStateWithLifecycle()
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
        ModelCards(
            models = ai.models,
            installed = model,
            transfer = transfer,
            icon = Symbols.InsertDriveFile,
            rejected = R.string.settings_ai_model_rejected,
            actions = ModelActions(ai::download, ai::import, ai::cancel, ai::remove)
        )
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
