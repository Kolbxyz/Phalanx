package org.phalanx.android.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.phalanx.android.R
import org.phalanx.android.ui.theme.Symbols

/** The Whisper models that write voice messages out, each downloaded in one tap. */
@Composable
internal fun SpeechModels(viewModel: SpeechSettingsViewModel = hiltViewModel()) {
    val model by viewModel.model.collectAsStateWithLifecycle()
    val transfer by viewModel.transfer.collectAsStateWithLifecycle()
    ModelCards(
        models = viewModel.models,
        installed = model,
        transfer = transfer,
        icon = Symbols.SpeechToText,
        rejected = R.string.settings_speech_model_rejected,
        actions = ModelActions(viewModel::download, viewModel::import, viewModel::cancel, viewModel::remove)
    )
}
