package org.phalanx.android.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.ui.theme.Symbols

/**
 * A Matrix ID that copies itself when tapped: the ID turns into "Copied" and the copy icon into a
 * check for a moment, so the tap is answered where it was made.
 */
@Composable
internal fun CopyIdChip(
    id: String,
    copyLabel: String,
    modifier: Modifier = Modifier,
    outlined: Boolean = false
) {
    val state = rememberCopyState()
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    Surface(
        onClick = { state.copy(copyLabel, id) },
        shape = RoundedCornerShape(16.dp),
        color = if (outlined) colors.surface else colors.surfaceContainerHighest,
        border = if (outlined) BorderStroke(1.dp, colors.outlineVariant) else null,
        modifier = modifier.heightIn(min = 32.dp)
    ) {
        AnimatedContent(
            targetState = state.copied,
            transitionSpec = {
                (fadeIn(motion.fastEffectsSpec()) + scaleIn(motion.fastSpatialSpec(), initialScale = 0.8f))
                    .togetherWith(fadeOut(motion.fastEffectsSpec()))
            },
            label = "copied"
        ) { copied ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(start = 12.dp, end = 10.dp, top = 6.dp, bottom = 6.dp)
            ) {
                Text(
                    text = if (copied) stringResource(R.string.copied) else id,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 260.dp)
                )
                Icon(
                    imageVector = if (copied) Symbols.Check else Symbols.ContentCopy,
                    contentDescription = if (copied) null else copyLabel,
                    tint = colors.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
