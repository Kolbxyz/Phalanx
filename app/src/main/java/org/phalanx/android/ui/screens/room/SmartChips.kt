package org.phalanx.android.ui.screens.room

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.phalanx.android.core.format.EntityAction
import org.phalanx.android.core.format.EntityKind
import org.phalanx.android.core.format.FoundEntity
import org.phalanx.android.ui.components.riseIn
import org.phalanx.android.ui.components.stagger
import org.phalanx.android.ui.theme.Symbols

/**
 * What the phone found in the message as chips under the lifted bubble, one per thing it can do:
 * "Add to calendar" over the date it would add, "Open in maps" over the address.
 */
@Composable
internal fun SmartChips(found: List<FoundEntity>, onAction: (EntityAction) -> Unit, modifier: Modifier = Modifier) {
    val chips = found.flatMap { entity -> entity.actions.map { entity to it } }.take(MAX_CHIPS)
    if (chips.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        chips.forEachIndexed { index, (entity, action) ->
            Surface(
                onClick = { onAction(action) },
                shape = RoundedCornerShape(26.dp),
                color = colors.tertiaryContainer,
                contentColor = colors.onTertiaryContainer,
                modifier = Modifier.heightIn(min = 52.dp).riseIn(stagger(index, CHIPS_FIRST, CHIPS_STEP))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = colors.onTertiaryContainer,
                        contentColor = colors.tertiaryContainer
                    ) {
                        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                            Icon(entity.kind.icon, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    }
                    Column {
                        Text(
                            action.title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            entity.text,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private const val MAX_CHIPS = 3
private const val CHIPS_FIRST = 200L
private const val CHIPS_STEP = 60L

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
