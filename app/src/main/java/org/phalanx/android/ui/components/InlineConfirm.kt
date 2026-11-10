package org.phalanx.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.phalanx.android.R

/**
 * A destructive choice that asks in place instead of in a dialog: the row's label turns into the
 * question and the consequence and the two buttons open beneath it, so the answer is where the
 * finger already is and nothing covers what is being acted on.
 */
@Composable
internal fun InlineConfirm(
    icon: ImageVector,
    label: String,
    question: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = DangerShape
) {
    var asking by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val state = stringResource(if (asking) R.string.confirm_expanded else R.string.confirm_collapsed)
    Column(modifier.fillMaxWidth().clip(shape).background(colors.errorContainer)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(role = Role.Button) { asking = !asking }
                .semantics { stateDescription = state }
                .padding(horizontal = 16.dp)
        ) {
            Icon(icon, contentDescription = null, tint = colors.onErrorContainer)
            Text(
                text = if (asking) question else label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (asking) FontWeight.SemiBold else null,
                color = colors.onErrorContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        AnimatedVisibility(
            visible = asking,
            enter = expandVertically(motion.defaultEffectsSpec()) + fadeIn(motion.defaultEffectsSpec()),
            exit = shrinkVertically(motion.fastEffectsSpec()) + fadeOut(motion.fastEffectsSpec())
        ) {
            Column {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onErrorContainer,
                    modifier = Modifier.padding(start = 56.dp, end = 16.dp, bottom = 12.dp)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                ) {
                    TextButton(
                        onClick = { asking = false },
                        colors = ButtonDefaults.textButtonColors(contentColor = colors.onErrorContainer)
                    ) { Text(stringResource(R.string.cancel)) }
                    Button(
                        onClick = { asking = false; onConfirm() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.error,
                            contentColor = colors.onError
                        )
                    ) { Text(confirmLabel) }
                }
            }
        }
    }
}

/** The danger block's corners: a little tighter than a card, the same as a segmented group's ends. */
internal val DangerShape = RoundedCornerShape(24.dp)
