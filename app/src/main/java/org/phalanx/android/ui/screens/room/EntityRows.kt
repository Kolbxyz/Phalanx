package org.phalanx.android.ui.screens.room

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.phalanx.android.core.format.EntityAction
import org.phalanx.android.core.format.EntityKind
import org.phalanx.android.core.format.FoundEntity
import org.phalanx.android.ui.components.SegmentGap
import org.phalanx.android.ui.components.segmentedShape
import org.phalanx.android.ui.theme.Symbols

/** One row per thing found in the message, with what the phone offers to do with it. */
@Composable
internal fun EntityRows(found: List<FoundEntity>, onAction: (EntityAction) -> Unit) {
    if (found.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(SegmentGap)) {
        found.forEachIndexed { index, entity ->
            ListItem(
                headlineContent = { Text(entity.text, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                leadingContent = { Icon(entity.kind.icon, contentDescription = null) },
                supportingContent = {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        entity.actions.forEach { action ->
                            AssistChip(onClick = { onAction(action) }, label = { Text(action.title) })
                        }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier
                    .clip(segmentedShape(index, found.size))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            )
        }
    }
}

private val EntityKind.icon: ImageVector
    @Composable get() = when (this) {
        EntityKind.Date -> Symbols.Event
        EntityKind.Phone -> Symbols.Call
        EntityKind.Email -> Symbols.Mail
        EntityKind.Address -> Symbols.LocationOn
        EntityKind.Flight -> Symbols.Flight
    }

/**
 * Starts the action's app. The intent was made by the system's classifier, so since Android 14
 * Phalanx must lend it the right to open an activity, which it has while the user taps on screen.
 * False when the intent is no longer valid.
 */
@Suppress("DEPRECATION")
internal fun EntityAction.launch(context: Context): Boolean {
    val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            .toBundle()
    } else {
        null
    }
    return try {
        intent.send(context, 0, null, null, null, null, options)
        true
    } catch (_: PendingIntent.CanceledException) {
        false
    }
}
