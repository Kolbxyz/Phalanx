package org.phalanx.android.ui.screens.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.core.ai.KnownModel

/** What a model is for, shown before its file name, which means little to most people. */
@get:StringRes
internal val KnownModel.role: Int
    get() = when {
        "Q4_K_M" in name -> R.string.model_role_lighter
        "Q5_K_M" in name -> R.string.model_role_precise
        "base" in name -> R.string.model_role_fast
        "small" in name -> R.string.model_role_balanced
        "large" in name -> R.string.model_role_accurate
        else -> R.string.model_role_other
    }

/** The installed model's short name, for a feature's status; [name] is one of these models. */
@StringRes
internal fun List<KnownModel>.shortNameOf(name: String): Int? = firstOrNull { it.name == name }?.let {
    when {
        "Q4_K_M" in name -> R.string.model_short_ministral_q4
        "Q5_K_M" in name -> R.string.model_short_ministral_q5
        "base" in name -> R.string.model_short_whisper_base
        "small" in name -> R.string.model_short_whisper_small
        "large" in name -> R.string.model_short_whisper_large
        else -> R.string.model_role_other
    }
}

/** One line under a section's title: what its models do, in plain words. */
@Composable
internal fun SectionLead(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
    )
}
