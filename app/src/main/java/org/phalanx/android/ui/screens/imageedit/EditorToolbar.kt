package org.phalanx.android.ui.screens.imageedit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.ui.theme.Symbols

internal enum class EditorTool { Crop, Draw, Blur }

/** Ink colours to pick from. */
internal val InkColors = listOf(
    Color.White,
    Color.Black,
    Color(0xFFE53935),
    Color(0xFFFB8C00),
    Color(0xFFFDD835),
    Color(0xFF43A047),
    Color(0xFF1E88E5),
    Color(0xFF8E24AA),
    Color(0xFFEC407A)
)

@Composable
internal fun EditorToolbar(
    tool: EditorTool,
    onTool: (EditorTool) -> Unit,
    color: Color,
    onColor: (Color) -> Unit,
    onRotate: () -> Unit,
    onFlip: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (tool == EditorTool.Draw) {
            LazyRow(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)
            ) {
                items(InkColors) { swatch -> Swatch(swatch, swatch == color) { onColor(swatch) } }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ToolButton(Symbols.Crop, R.string.editor_crop, tool == EditorTool.Crop) { onTool(EditorTool.Crop) }
            ToolButton(Symbols.Brush, R.string.editor_draw, tool == EditorTool.Draw) { onTool(EditorTool.Draw) }
            ToolButton(Symbols.BlurOn, R.string.editor_blur, tool == EditorTool.Blur) { onTool(EditorTool.Blur) }
            IconButton(onClick = onRotate) {
                Icon(Symbols.RotateRight, contentDescription = stringResource(R.string.editor_rotate))
            }
            IconButton(onClick = onFlip) {
                Icon(Symbols.Flip, contentDescription = stringResource(R.string.editor_flip))
            }
        }
    }
}

@Composable
private fun ToolButton(icon: ImageVector, label: Int, selected: Boolean, onClick: () -> Unit) {
    FilledTonalIconToggleButton(checked = selected, onCheckedChange = { onClick() }) {
        Icon(icon, contentDescription = stringResource(label))
    }
}

@Composable
private fun Swatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    val ring = MaterialTheme.colorScheme.primary
    val label = stringResource(R.string.editor_colour)
    Row(
        Modifier
            .size(32.dp)
            .background(color, CircleShape)
            .border(
                if (selected) 3.dp else 1.dp,
                if (selected) ring else MaterialTheme.colorScheme.outline,
                CircleShape
            )
            .clickable(role = Role.RadioButton, onClickLabel = label, onClick = onClick)
    ) {}
}
