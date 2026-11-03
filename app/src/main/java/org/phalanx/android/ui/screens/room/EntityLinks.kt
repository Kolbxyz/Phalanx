package org.phalanx.android.ui.screens.room

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.phalanx.android.R
import org.phalanx.android.core.format.EntityAction
import org.phalanx.android.core.format.EntitySpan
import org.phalanx.android.core.format.FoundEntity
import org.phalanx.android.core.format.TextEntities
import org.phalanx.android.data.model.TextMarks
import org.phalanx.android.ui.components.ModalSheet

/** The app's [TextEntities], provided at the root; null where nothing is detected, as in previews. */
internal val LocalTextEntities = staticCompositionLocalOf<TextEntities?> { null }

// A message scrolled past quicker than this is never sent to the classifier.
private const val SETTLE_MILLIS = 150L

/** What the classifier found in [text]: at once when known, else once the message stays on screen. */
@Composable
internal fun rememberEntitySpans(text: String): List<EntitySpan> {
    val entities = LocalTextEntities.current
    val spans by produceState(entities?.cached(text).orEmpty(), text, entities) {
        if (entities != null && entities.cached(text) == null) {
            delay(SETTLE_MILLIS)
            value = entities.spans(text)
        }
    }
    return spans
}

/** Underlines each of [spans] that no link, mention or code already covers; a tap goes to [onTap]. */
internal fun AnnotatedString.withEntityLinks(
    spans: List<EntitySpan>,
    styles: TextLinkStyles,
    onTap: (EntitySpan) -> Unit
): AnnotatedString {
    if (spans.isEmpty()) return this
    val source = this
    return buildAnnotatedString {
        append(source)
        spans.filter { it.end <= source.length }.forEach { span ->
            val taken = source.getLinkAnnotations(span.start, span.end).isNotEmpty() ||
                source.getStringAnnotations(TextMarks.CODE, span.start, span.end).isNotEmpty()
            if (!taken) {
                val link = LinkAnnotation.Clickable("entity", styles) { onTap(span) }
                addLink(link, span.start, span.end)
            }
        }
    }
}

/** The actions for a tapped date, number or address, asked of the classifier only now. */
@Composable
internal fun EntitySheet(text: String, span: EntitySpan, onDismiss: () -> Unit) {
    val entities = LocalTextEntities.current
    val context = LocalContext.current
    val shown = remember(text, span) { text.substring(span.start, span.end) }
    val actions by produceState<List<EntityAction>?>(null, text, span) {
        value = entities?.actions(text, span).orEmpty()
    }
    var failed by remember { mutableStateOf(false) }
    ModalSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            val loaded = actions
            when {
                loaded == null -> LoadingIndicator(Modifier.align(Alignment.CenterHorizontally))
                loaded.isEmpty() -> Text(stringResource(R.string.entity_no_action, shown))
                else -> EntityRows(listOf(FoundEntity(shown, span.kind, loaded))) { action ->
                    if (action.launch(context)) onDismiss() else failed = true
                }
            }
            if (failed) {
                Text(
                    text = stringResource(R.string.entity_action_failed),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
