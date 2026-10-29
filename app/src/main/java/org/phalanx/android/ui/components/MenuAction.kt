package org.phalanx.android.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalToggleButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.TonalToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One choice of a long-press menu. [checked] is set for an on/off one, drawn as a toggle up front. */
@Immutable
internal data class MenuAction(
    val icon: ImageVector,
    @StringRes val label: Int,
    val onClick: () -> Unit,
    val checked: Boolean? = null
)

/**
 * A long-press menu's choices, most used first: the first [QUICK] as big buttons in one row, where
 * the thumb is, then the rest as a segmented list, and [danger] apart at the foot in the error
 * colour, so nothing destructive sits next to an everyday choice.
 */
@Composable
internal fun MenuActions(
    actions: List<MenuAction>,
    danger: List<MenuAction> = emptyList(),
    /** Rows of the menu's own, shown after the list and before [danger]. */
    extra: @Composable () -> Unit = {}
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        val quick = actions.take(QUICK)
        if (quick.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                quick.forEachIndexed { index, action -> QuickButton(action, lead = index == 0) }
            }
        }
        MenuList(actions.drop(QUICK), destructive = false)
        extra()
        MenuList(danger, destructive = true)
    }
}

/** Icon over label, rounder at rest than pressed: the corners tighten under the finger. */
@Composable
private fun RowScope.QuickButton(action: MenuAction, lead: Boolean) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.extraLarge
    val pressed = MaterialTheme.shapes.medium
    val modifier = Modifier.weight(1f).height(QUICK_HEIGHT)
    val padding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
    val checked = action.checked
    if (checked == null) {
        FilledTonalButton(
            onClick = action.onClick,
            shapes = ButtonDefaults.shapes(shape = shape, pressedShape = pressed),
            // The first choice is what the menu is opened for most; it alone takes the accent.
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = if (lead) colors.primaryContainer else colors.surfaceContainerHighest,
                contentColor = if (lead) colors.onPrimaryContainer else colors.onSurface
            ),
            contentPadding = padding,
            modifier = modifier
        ) { QuickContent(action) }
    } else {
        TonalToggleButton(
            checked = checked,
            onCheckedChange = { action.onClick() },
            shapes = ToggleButtonShapes(
                shape = shape,
                pressedShape = pressed,
                checkedShape = MaterialTheme.shapes.large
            ),
            colors = FilledTonalToggleButtonDefaults.filledTonalToggleButtonColors(
                containerColor = colors.surfaceContainerHighest,
                contentColor = colors.onSurface,
                checkedContainerColor = colors.secondaryContainer,
                checkedContentColor = colors.onSecondaryContainer
            ),
            contentPadding = padding,
            modifier = modifier
        ) { QuickContent(action) }
    }
}

@Composable
private fun QuickContent(action: MenuAction) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(action.icon, contentDescription = null, modifier = Modifier.size(24.dp))
        // Two lines at most, stepping down rather than cut: a longer label in another language still fits.
        val style = MaterialTheme.typography.labelMedium
        Text(
            text = stringResource(action.label),
            style = style,
            maxLines = 2,
            textAlign = TextAlign.Center,
            autoSize = TextAutoSize.StepBased(minFontSize = MIN_LABEL, maxFontSize = style.fontSize)
        )
    }
}

@Composable
private fun MenuList(actions: List<MenuAction>, destructive: Boolean) {
    if (actions.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    val container = if (destructive) colors.errorContainer else colors.surfaceContainerHigh
    val content = if (destructive) colors.onErrorContainer else colors.onSurface
    Column(verticalArrangement = Arrangement.spacedBy(SegmentGap)) {
        actions.forEachIndexed { index, action ->
            ListItem(
                headlineContent = { Text(stringResource(action.label)) },
                leadingContent = { Icon(action.icon, contentDescription = null) },
                colors = ListItemDefaults.colors(
                    containerColor = Color.Transparent,
                    headlineColor = content,
                    leadingIconColor = content
                ),
                modifier = Modifier
                    .clip(segmentedShape(index, actions.size))
                    .background(container)
                    .clickable(role = Role.Button, onClick = action.onClick)
            )
        }
    }
}

private const val QUICK = 4
private val QUICK_HEIGHT = 84.dp
private val MIN_LABEL = 10.sp
