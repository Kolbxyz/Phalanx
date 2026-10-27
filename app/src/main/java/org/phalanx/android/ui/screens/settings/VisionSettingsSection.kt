package org.phalanx.android.ui.screens.settings

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.phalanx.android.R
import org.phalanx.android.ui.components.SegmentedRow
import org.phalanx.android.ui.components.SegmentedSection
import org.phalanx.android.ui.components.segmentedShape
import org.phalanx.android.ui.theme.Symbols

/** The vision projector that lets the language model read photos, downloaded in one tap. */
@Composable
internal fun VisionSettingsSection(viewModel: VisionSettingsViewModel = hiltViewModel()) {
    val model by viewModel.model.collectAsStateWithLifecycle()
    val transfer by viewModel.transfer.collectAsStateWithLifecycle()
    SegmentedSection(stringResource(R.string.settings_vision_section)) {
        ModelCards(
            models = viewModel.models,
            installed = model,
            transfer = transfer,
            icon = Symbols.Image,
            rejected = R.string.settings_vision_model_rejected,
            actions = ModelActions(viewModel::download, viewModel::import, viewModel::cancel, viewModel::remove)
        )
        SegmentedRow(
            headline = stringResource(R.string.settings_vision_hint_title),
            supporting = stringResource(R.string.settings_vision_hint),
            shape = segmentedShape(0, 1),
            leading = { Icon(Symbols.Info, contentDescription = null) }
        )
    }
}
