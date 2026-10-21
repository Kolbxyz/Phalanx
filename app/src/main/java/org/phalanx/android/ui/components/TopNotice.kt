package org.phalanx.android.ui.components

import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.phalanx.android.ui.theme.Symbols

enum class NoticeKind { Info, Done, Scheduled, Error }

/** A short message dropped in from the top of the screen. [atMillis] adds a date and time line. */
@Immutable
data class Notice(@StringRes val text: Int, val kind: NoticeKind = NoticeKind.Info, val atMillis: Long? = null)

private const val VISIBLE_MILLIS = 4_000L
private const val DISMISS_DRAG_PX = -24f

/**
 * Shows [notice] under the status bar until it times out or is flicked up, with the theme's
 * expressive spatial spring. A new notice replaces the current one.
 */
@Composable
fun TopNoticeHost(notice: Notice?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    // Kept while the exit animation runs, after the caller already cleared it.
    var shown by remember { mutableStateOf(notice) }
    if (notice != null) shown = notice
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(VISIBLE_MILLIS)
            onDismiss()
        }
    }
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = notice != null,
        enter = slideInVertically(motion.defaultSpatialSpec()) { -it * 2 } + fadeIn(motion.defaultEffectsSpec()),
        exit = slideOutVertically(motion.fastSpatialSpec()) { -it * 2 } + fadeOut(motion.fastEffectsSpec()),
        modifier = modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Box(Modifier.fillMaxWidth(), Alignment.TopCenter) {
            shown?.let { NoticeCard(it, onDismiss) }
        }
    }
}

@Composable
private fun NoticeCard(notice: Notice, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val (container, content, accent) = when (notice.kind) {
        NoticeKind.Error -> Triple(colors.errorContainer, colors.onErrorContainer, colors.error)
        NoticeKind.Scheduled -> Triple(colors.tertiaryContainer, colors.onTertiaryContainer, colors.tertiary)
        NoticeKind.Done -> Triple(colors.primaryContainer, colors.onPrimaryContainer, colors.primary)
        NoticeKind.Info -> Triple(colors.inverseSurface, colors.inverseOnSurface, colors.inversePrimary)
    }
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = container,
        contentColor = content,
        shadowElevation = 6.dp,
        modifier = Modifier
            .widthIn(max = 560.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta -> if (delta < DISMISS_DRAG_PX) onDismiss() }
            )
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = MaterialShapes.Cookie9Sided.toShape(), color = accent, contentColor = container) {
                Icon(iconOf(notice.kind), contentDescription = null, modifier = Modifier.padding(10.dp).size(24.dp))
            }
            Column(Modifier.weight(1f, fill = false)) {
                notice.atMillis?.let {
                    Text(
                        text = DateUtils.formatDateTime(
                            context,
                            it,
                            DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY
                        ),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Text(
                    text = stringResource(notice.text),
                    style = if (notice.atMillis == null) {
                        MaterialTheme.typography.titleSmall
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                    color = if (notice.atMillis == null) Color.Unspecified else content.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
private fun iconOf(kind: NoticeKind): ImageVector = when (kind) {
    NoticeKind.Info -> Symbols.Info
    NoticeKind.Done -> Symbols.CheckCircle
    NoticeKind.Scheduled -> Symbols.Schedule
    NoticeKind.Error -> Symbols.ErrorOutline
}
