package org.phalanx.android.ui.screens.room

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.phalanx.android.core.format.MessageSplitter
import org.phalanx.android.core.format.OutgoingMessage
import org.phalanx.android.data.model.AppSettings
import org.phalanx.android.data.model.RoomSummary
import org.phalanx.android.ui.screens.people.sourceOf
import org.phalanx.android.ui.screens.roomlist.label

/**
 * What sits above the composer: in a person's thread, which network the message goes through, and
 * in any bridged chat, how the draft will be split for that network, with a preview of where. The
 * [composer] is given how many messages the draft goes out as, for its send button.
 */
@Composable
internal fun ComposerBar(
    draft: String,
    mode: ComposerMode,
    settings: AppSettings?,
    room: RoomSummary?,
    thread: PersonThread?,
    roomOf: (entryId: String) -> String,
    onChooseNetwork: (String) -> Unit,
    composer: @Composable (parts: Int) -> Unit
) {
    val target = thread?.target ?: room
    val budgetOf = { chat: RoomSummary -> settings?.let { OutgoingMessage.budget(it, chat.bridge) } }
    val budget = target?.let(budgetOf)
    val parts = remember(draft, settings, target?.bridge) {
        if (settings == null || budget == null || draft.isBlank()) {
            emptyList()
        } else {
            OutgoingMessage.split(draft, settings, target?.bridge)
        }
    }
    val split = parts.size > 1
    val near = budget != null && draft.length * NEAR_DENOMINATOR >= budget * NEAR_NUMERATOR
    var previewing by rememberSaveable { mutableStateOf(false) }
    if (!split) previewing = false
    Column {
        val motion = MaterialTheme.motionScheme
        AnimatedVisibility(
            visible = previewing && budget != null,
            enter = expandVertically(motion.defaultEffectsSpec()) + fadeIn(motion.defaultEffectsSpec()),
            exit = shrinkVertically(motion.defaultEffectsSpec()) + fadeOut(motion.fastEffectsSpec()),
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            val instead = thread?.chats?.firstOrNull { it.id != target?.id && budgetOf(it) == null }
            SplitPreview(
                parts = parts,
                network = target?.let { stringResource(sourceOf(it).label) }.orEmpty(),
                budget = budget ?: 1,
                instead = instead?.let { stringResource(sourceOf(it).label) },
                onSendInstead = {
                    previewing = false
                    instead?.let { onChooseNetwork(it.id) }
                },
                onClose = { previewing = false }
            )
        }
        if (thread != null || split || near) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 12.dp)
            ) {
                if (thread != null) NetworkPicker(thread, mode, roomOf, budgetOf, onChooseNetwork)
                Spacer(Modifier.weight(1f))
                AnimatedVisibility(
                    visible = (split || near) && budget != null,
                    enter = scaleIn(motion.defaultSpatialSpec(), initialScale = METER_FROM, transformOrigin = End) +
                        fadeIn(motion.fastEffectsSpec()),
                    exit = scaleOut(motion.fastSpatialSpec(), targetScale = METER_FROM, transformOrigin = End) +
                        fadeOut(motion.fastEffectsSpec())
                ) {
                    SplitMeter(parts.ifEmpty { listOf(MessageSplitter.Part(draft, null)) }, budget ?: 1, draft.length) {
                        previewing = !previewing
                    }
                }
            }
        }
        composer(if (split) parts.size else 1)
    }
}

private val End = TransformOrigin(1f, 0.5f)
private const val METER_FROM = 0.6f

// The meter shows once the draft reaches four fifths of what the network takes.
private const val NEAR_NUMERATOR = 4
private const val NEAR_DENOMINATOR = 5
