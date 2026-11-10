package org.phalanx.android.ui.screens.roomlist

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun UnreadDot() {
    val description = stringResource(R.string.roomlist_marked_unread)
    Box(
        Modifier
            .size(12.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape)
            .semantics { contentDescription = description }
    )
}

@Composable
internal fun UnreadBadge(count: Long, mention: Boolean, muted: Boolean) {
    val quantity = count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    val description = pluralStringResource(R.plurals.roomlist_unread, quantity, count)
    Surface(
        shape = CircleShape,
        // A muted room still counts its messages, quietly; a mention of this account always stands out.
        color = when {
            mention -> MaterialTheme.colorScheme.error
            muted -> MaterialTheme.colorScheme.surfaceVariant
            else -> MaterialTheme.colorScheme.primary
        },
        contentColor = when {
            mention -> MaterialTheme.colorScheme.onError
            muted -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.onPrimary
        },
        modifier = Modifier
            .defaultMinSize(minWidth = 24.dp, minHeight = 24.dp)
            .semantics { contentDescription = description }
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 7.dp)) {
            // A new message rolls the count up, like an odometer.
            val motion = MaterialTheme.motionScheme
            AnimatedContent(
                targetState = count,
                transitionSpec = {
                    (slideInVertically(motion.fastSpatialSpec()) { it } + fadeIn(motion.fastEffectsSpec())) togetherWith
                        (slideOutVertically(motion.fastSpatialSpec()) { -it } + fadeOut(motion.fastEffectsSpec()))
                }
            ) { shown ->
                Text(
                    text = if (shown > MAX_BADGE) "$MAX_BADGE+" else shown.toString(),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

private const val MAX_BADGE = 99L

/** Today shows the time; anything older shows the date. */
internal fun formatActivity(millis: Long): String {
    val zone = ZoneId.systemDefault()
    val moment = Instant.ofEpochMilli(millis).atZone(zone)
    val formatter = if (moment.toLocalDate() == LocalDate.now(zone)) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    } else {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
    }
    return formatter.format(moment)
}
