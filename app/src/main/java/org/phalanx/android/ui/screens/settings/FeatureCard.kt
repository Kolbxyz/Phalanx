package org.phalanx.android.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import org.phalanx.android.ui.theme.Symbols

/** Where a feature stands: [label] says it in words, [ready] colours it. */
internal class FeatureStatus(val label: String, val ready: Boolean)

/**
 * One AI feature as an expressive card: its icon in [shape], what it does, whether it is ready,
 * and its switch when it has one. Tapping the card opens [content], the models it runs on; it
 * starts open while the feature still needs one.
 */
@Composable
internal fun FeatureCard(
    icon: ImageVector,
    shape: RoundedPolygon,
    title: String,
    description: String,
    status: FeatureStatus,
    checked: Boolean? = null,
    onCheckedChange: (Boolean) -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    var open by rememberSaveable { mutableStateOf(!status.ready) }
    val motion = MaterialTheme.motionScheme
    val chevron by animateFloatAsState(if (open) HALF_TURN else 0f, motion.defaultSpatialSpec())
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .clickable(role = Role.Button) { open = !open }
                    .semantics { stateDescription = status.label }
                    .padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(shape.toShape())
                        .background(if (status.ready) colors.primaryContainer else colors.tertiaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (status.ready) colors.onPrimaryContainer else colors.onTertiaryContainer
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text(description, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    StatusChip(status)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (checked != null) Switch(checked = checked, onCheckedChange = onCheckedChange)
                    Icon(
                        Symbols.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .rotate(chevron)
                    )
                }
            }
            AnimatedVisibility(
                visible = open,
                enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.defaultEffectsSpec())
            ) {
                Column(
                    Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
private fun StatusChip(status: FeatureStatus) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .padding(top = 4.dp)
            .clip(MaterialTheme.shapes.small)
            .background(if (status.ready) colors.secondaryContainer else colors.errorContainer)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (status.ready) Symbols.Check else Symbols.Download,
            contentDescription = null,
            tint = if (status.ready) colors.onSecondaryContainer else colors.onErrorContainer,
            modifier = Modifier.size(16.dp)
        )
        Text(
            status.label,
            style = MaterialTheme.typography.labelLarge,
            color = if (status.ready) colors.onSecondaryContainer else colors.onErrorContainer
        )
    }
}

/** The shapes the features' icons sit in, one each, so the cards tell apart at a glance. */
internal object FeatureShapes {
    val Summaries get() = MaterialShapes.Cookie9Sided
    val Voice get() = MaterialShapes.Pill
    val Translation get() = MaterialShapes.Clover4Leaf
}

private const val HALF_TURN = 180f
