package org.phalanx.android.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.phalanx.android.R
import org.phalanx.android.data.model.AppSettings
import org.phalanx.android.ui.components.SegmentedRow
import org.phalanx.android.ui.components.segmentedShape
import org.phalanx.android.ui.theme.Symbols

/**
 * The on-device AI, as one card per feature: what it does, whether it is ready, its switch, and,
 * opened, the models it runs on. Keeping results on the phone and where downloads come from close it.
 */
@Composable
internal fun AiSettings(
    current: AppSettings,
    viewModel: SettingsViewModel,
    ai: AiSettingsViewModel = hiltViewModel(),
    speech: SpeechSettingsViewModel = hiltViewModel(),
    translation: TranslationSettingsViewModel = hiltViewModel()
) {
    if (!ai.supported) {
        SegmentedRow(
            headline = stringResource(R.string.settings_ai_unsupported),
            shape = segmentedShape(0, 1),
            leading = { Icon(Symbols.Info, contentDescription = null) }
        )
        return
    }
    val model by ai.model.collectAsStateWithLifecycle()
    val voice by speech.model.collectAsStateWithLifecycle()
    val pairs by translation.pairs.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLead(R.string.settings_ai_intro)
        FeatureCard(
            icon = Symbols.Summarize,
            shape = FeatureShapes.Summaries,
            title = stringResource(R.string.feature_summaries),
            description = stringResource(R.string.feature_summaries_description),
            status = statusOf(model?.let { ai.models.shortNameOf(it) }),
            checked = current.aiSummaries,
            onCheckedChange = viewModel::setAiSummaries
        ) { LanguageModels(ai) }
        FeatureCard(
            icon = Symbols.SpeechToText,
            shape = FeatureShapes.Voice,
            title = stringResource(R.string.feature_voice),
            description = stringResource(R.string.feature_voice_description),
            status = statusOf(voice?.let { speech.models.shortNameOf(it) }),
            checked = current.autoTranscribe,
            onCheckedChange = viewModel::setAutoTranscribe
        ) { SpeechModels(speech) }
        FeatureCard(
            icon = Symbols.Translate,
            shape = FeatureShapes.Translation,
            title = stringResource(R.string.feature_translation),
            description = stringResource(R.string.feature_translation_description),
            status = if (pairs.isEmpty()) {
                FeatureStatus(stringResource(R.string.feature_needs_pair), ready = false)
            } else {
                FeatureStatus(stringResource(R.string.feature_ready, pairs.joinToString { labelOf(it) }), ready = true)
            }
        ) { TranslationModels(translation) }
        SwitchGroup(
            listOf(
                SwitchSetting(
                    Symbols.Lock, R.string.settings_ai_on_disk, R.string.settings_ai_on_disk_summary,
                    current.aiOnDisk, viewModel::setAiOnDisk
                )
            )
        )
        SectionLead(R.string.settings_ai_downloads_note)
    }
}

/** Ready with [installed]'s short name, or needing a model. */
@Composable
private fun statusOf(installed: Int?): FeatureStatus = if (installed == null) {
    FeatureStatus(stringResource(R.string.feature_needs_model), ready = false)
} else {
    FeatureStatus(stringResource(R.string.feature_ready, stringResource(installed)), ready = true)
}

/** The Ministral models every text feature runs on. */
@Composable
private fun LanguageModels(ai: AiSettingsViewModel) {
    val model by ai.model.collectAsStateWithLifecycle()
    val transfer by ai.transfer.collectAsStateWithLifecycle()
    ModelCards(
        models = ai.models,
        installed = model,
        transfer = transfer,
        icon = Symbols.Summarize,
        rejected = R.string.settings_ai_model_rejected,
        actions = ModelActions(ai::download, ai::import, ai::cancel, ai::remove)
    )
}
