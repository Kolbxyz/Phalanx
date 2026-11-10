package org.phalanx.android.ui.screens.room

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import org.phalanx.android.R
import org.phalanx.android.data.model.RoomSummary
import org.phalanx.android.ui.screens.people.sourceOf
import org.phalanx.android.ui.screens.roomlist.SourceIcon
import org.phalanx.android.ui.screens.roomlist.label
import org.phalanx.android.ui.theme.Symbols

/**
 * Which network the next message goes through, above the composer of a person's thread. A reply or
 * an edit goes where its message came from, so [mode] fixes it then and the choice is not offered.
 * Each network in the list says how long a message it takes, from [budgetOf], so the choice can be
 * made for a long message before it is split.
 */
@Composable
internal fun NetworkPicker(
    thread: PersonThread,
    mode: ComposerMode,
    roomOf: (entryId: String) -> String,
    budgetOf: (RoomSummary) -> Int?,
    onChoose: (String) -> Unit
) {
    val message = (mode as? ComposerMode.Reply)?.message ?: (mode as? ComposerMode.Edit)?.message
    val replyingIn = message?.let { thread.chats.firstOrNull { chat -> chat.id == roomOf(it.id) } }
    val shown = replyingIn ?: thread.target
    var open by remember { mutableStateOf(false) }
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme
    val turn by animateFloatAsState(if (open) HALF_TURN else 0f, motion.defaultSpatialSpec())
    Box {
        Surface(
            onClick = { open = true },
            enabled = replyingIn == null,
            shape = RoundedCornerShape(16.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.outlineVariant),
            modifier = Modifier.heightIn(min = 32.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(start = 6.dp, end = 8.dp)
            ) {
                // Follows the others to the network they last wrote from: the change has to be seen.
                AnimatedContent(
                    targetState = shown,
                    contentKey = { it.id },
                    transitionSpec = {
                        val rise = slideInVertically(motion.fastSpatialSpec()) { it / RISE_DIVISOR }
                        (fadeIn(motion.fastEffectsSpec()) + rise).togetherWith(fadeOut(motion.fastEffectsSpec()))
                    }
                ) { chat ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SourceIcon(sourceOf(chat), size = 20.dp)
                        Text(
                            text = stringResource(R.string.composer_network, stringResource(sourceOf(chat).label)),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
                if (replyingIn == null) {
                    Icon(
                        Symbols.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = turn }
                    )
                }
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = colors.surfaceContainerHigh,
            modifier = Modifier.width(284.dp)
        ) {
            thread.chats.forEach { chat ->
                NetworkRow(chat, budgetOf(chat), current = chat.id == shown.id) {
                    open = false
                    onChoose(chat.id)
                }
            }
        }
    }
}

@Composable
private fun NetworkRow(chat: RoomSummary, budget: Int?, current: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val note = if (budget != null) {
        stringResource(R.string.network_limit, NumberFormat.getIntegerInstance().format(budget))
    } else {
        stringResource(R.string.network_no_limit)
    }
    DropdownMenuItem(
        text = {
            Column {
                Text(
                    stringResource(sourceOf(chat).label),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(note, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        },
        leadingIcon = { SourceIcon(sourceOf(chat), size = 28.dp) },
        trailingIcon = if (current) {
            { Icon(Symbols.Check, contentDescription = null, tint = colors.primary) }
        } else {
            null
        },
        onClick = onClick,
        modifier = Modifier
            .padding(horizontal = 6.dp)
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(14.dp))
            .then(if (current) Modifier.background(colors.secondaryContainer) else Modifier)
    )
}

private const val HALF_TURN = 180f

// The new network rises a third of its height into the chip.
private const val RISE_DIVISOR = 3
