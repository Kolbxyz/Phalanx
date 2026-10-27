package org.phalanx.android.ui.screens.room

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import org.phalanx.android.R
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.ui.theme.Symbols

private const val MUTED = 0.7f

/**
 * A text message, or its translation when the room auto-translates and it was written in the
 * pair's source language. Tapping the translation shows the message as written, and tapping the
 * note under it shows the translation again. A long press still opens the message's menu.
 */
@Composable
internal fun TranslatableText(
    key: String,
    body: MessageBody.Text,
    original: @Composable () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val auto = LocalAutoTranslations.current ?: return original()
    val pair by auto.pair.collectAsStateWithLifecycle()
    val translations by auto.translations.collectAsStateWithLifecycle()
    val originals by auto.originals.collectAsStateWithLifecycle()
    LaunchedEffect(key, pair) { auto.translateOnSight(key, body.text) }
    val from = pair?.from
    val translated = translations[key]?.takeIf { from != null }
    when {
        translated == null || from == null -> original()
        key in originals -> Column {
            original()
            Note(
                stringResource(R.string.auto_translate_show_translation),
                Modifier
                    .combinedClickable(role = Role.Button, onLongClick = onLongPress) { auto.toggleOriginal(key) }
                    .padding(start = 14.dp, end = 14.dp, top = 4.dp)
            )
        }
        else -> Column(
            Modifier
                .combinedClickable(role = Role.Button, onLongClick = onLongPress) { auto.toggleOriginal(key) }
                .then(modifier)
                .padding(bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(translated, style = MaterialTheme.typography.bodyLarge)
            Note(stringResource(R.string.auto_translated_from, displayName(from)), Modifier)
        }
    }
}

@Composable
private fun Note(text: String, modifier: Modifier) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Symbols.Translate,
            contentDescription = null,
            tint = LocalContentColor.current.copy(alpha = MUTED),
            modifier = Modifier.size(14.dp)
        )
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = LocalContentColor.current.copy(alpha = MUTED)
        )
    }
}

private fun displayName(code: String): String = Locale.forLanguageTag(code).let { it.getDisplayLanguage(it) }
    .replaceFirstChar { it.titlecase(Locale.getDefault()) }
