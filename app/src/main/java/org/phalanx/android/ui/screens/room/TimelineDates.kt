package org.phalanx.android.ui.screens.room

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import org.phalanx.android.R
import org.phalanx.android.data.model.TimelineEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val WEEK_DAYS = 6L
private const val HIDE_AFTER_MILLIS = 1_000L

/** A centred date chip: "Today", "Yesterday", the weekday within a week, otherwise the date. */
@Composable
internal fun DayDivider(millis: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val label = remember(millis) { dayLabel(context, millis) }
    Box(modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .semantics { heading() }
            )
        }
    }
}

private fun dayLabel(context: Context, millis: Long): String {
    val zone = ZoneId.systemDefault()
    val day = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    val locale = context.resources.configuration.locales[0]
    val skeleton = when {
        day == today || day == today.minusDays(1) -> null
        day.isAfter(today.minusDays(WEEK_DAYS)) -> "EEEE"
        day.year == today.year -> "EEEdMMMM"
        else -> "EEEdMMMMyyyy"
    }
    return when (skeleton) {
        null -> context.getString(if (day == today) R.string.timeline_today else R.string.timeline_yesterday)
        else -> DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale).format(day)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }
}

/**
 * The day of the oldest visible message, shown at the top while the list scrolls and a moment
 * after, so the reader always knows where in the conversation they are.
 */
@Composable
internal fun FloatingDate(
    listState: LazyListState,
    entries: List<TimelineEntry>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // The list is reversed: the last visible item is the one at the top of the screen.
    val millis by remember(entries) {
        derivedStateOf {
            val top = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf null
            when (val entry = entries.getOrNull(top)) {
                is TimelineEntry.Message -> entry.timestampMillis
                is TimelineEntry.DayDivider -> entry.millis
                null -> null
            }
        }
    }
    var scrolled by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collectLatest { scrolling ->
            if (scrolling) {
                scrolled = true
            } else {
                delay(HIDE_AFTER_MILLIS)
                scrolled = false
            }
        }
    }
    val atNewest by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    val label = millis?.let { remember(it) { dayLabel(context, it) } }
    AnimatedVisibility(
        visible = scrolled && !atNewest && label != null,
        enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        modifier = modifier
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
            Text(
                text = label.orEmpty(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }
    }
}
