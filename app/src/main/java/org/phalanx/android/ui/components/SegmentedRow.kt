package org.phalanx.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.ui.theme.Symbols

/**
 * One strip of a segmented list (see [segmentedShape]). [interaction] is applied inside the clip,
 * so a click or toggle ripple stays within the strip's corners.
 */
@Composable
internal fun SegmentedRow(
    headline: String,
    shape: Shape,
    modifier: Modifier = Modifier,
    interaction: Modifier = Modifier,
    supporting: String? = null,
    headlineColor: Color = Color.Unspecified,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    ListItem(
        headlineContent = { Text(headline, color = headlineColor) },
        supportingContent = if (supporting != null) {
            { Text(supporting) }
        } else {
            null
        },
        leadingContent = leading,
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(interaction)
    )
}

/** A titled run of [SegmentedRow]s, separated by the segmented list's thin gap. */
@Composable
internal fun SegmentedSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
            .semantics { heading() }
    )
    Column(verticalArrangement = Arrangement.spacedBy(SegmentGap), content = content)
}

@Composable
internal fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(Symbols.ArrowBack, contentDescription = stringResource(R.string.back))
    }
}
