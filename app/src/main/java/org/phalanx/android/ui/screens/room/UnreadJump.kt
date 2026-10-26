package org.phalanx.android.ui.screens.room

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.ui.theme.Symbols

// The down arrow turned to point up, towards older messages.
private const val UPWARD_DEGREES = 180f

/** A pill at the top of the conversation: how many messages were unread, and a way back to the first. */
@Composable
internal fun UnreadJump(count: Int?, onJump: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val motion = MaterialTheme.motionScheme
    // The last count stays drawn while the pill leaves.
    var shown by remember { mutableIntStateOf(0) }
    if (count != null) shown = count
    AnimatedVisibility(
        visible = count != null,
        enter = slideInVertically(motion.defaultSpatialSpec()) { -it } + fadeIn(motion.defaultEffectsSpec()),
        exit = slideOutVertically(motion.fastSpatialSpec()) { -it } + fadeOut(motion.fastEffectsSpec()),
        modifier = modifier
    ) {
        val label = stringResource(R.string.timeline_jump_unread)
        Surface(
            onClick = onJump,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shadowElevation = 3.dp,
            modifier = Modifier.semantics { contentDescription = label }
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Symbols.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp).rotate(UPWARD_DEGREES)
                )
                Text(
                    pluralStringResource(R.plurals.roomlist_unread, shown, shown),
                    style = MaterialTheme.typography.labelLarge
                )
                IconButton(onClick = onDismiss) {
                    Icon(Symbols.Close, contentDescription = stringResource(R.string.timeline_jump_unread_dismiss))
                }
            }
        }
    }
}
