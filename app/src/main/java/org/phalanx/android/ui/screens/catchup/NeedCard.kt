package org.phalanx.android.ui.screens.catchup

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.ZonedDateTime
import org.phalanx.android.R
import org.phalanx.android.ui.components.MatrixAvatar
import org.phalanx.android.ui.components.riseIn
import org.phalanx.android.ui.components.segmentedShape
import org.phalanx.android.ui.components.stagger
import org.phalanx.android.ui.screens.room.TimePreset
import org.phalanx.android.ui.theme.Symbols

/**
 * One thing waiting on the user, as a card in the tertiary colour: who and where, what it is, and
 * what can be done right there. Replying is offered only for what is waiting on them.
 */
@Composable
internal fun NeedCard(
    need: Need,
    index: Int,
    count: Int,
    reminding: Reminding,
    onReply: () -> Unit,
    onRemind: (Reminding) -> Unit,
    onPick: (Long) -> Unit,
    onUndoReminder: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = segmentedShape(index, count),
        color = colors.tertiaryContainer,
        contentColor = colors.onTertiaryContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 16.dp, bottom = 8.dp)
        ) {
            NeedAvatar(need.card)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val label = if (need.kind == NeedKind.Waiting) {
                    R.string.catch_up_waiting_on_you
                } else {
                    R.string.catch_up_you_promised
                }
                Text(
                    stringResource(R.string.catch_up_need_label, stringResource(label), need.card.room.name),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.tertiary
                )
                Text(need.text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                val fade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                AnimatedContent(
                    targetState = reminding,
                    contentKey = { it::class },
                    transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) },
                    label = "reminding"
                ) { state ->
                    when (state) {
                        Reminding.Idle -> IdleActions(need.kind, onReply, { onRemind(Reminding.Picking) }, onDone)
                        Reminding.Picking -> PresetChips(onPick) { onRemind(Reminding.Idle) }
                        is Reminding.Set -> ReminderSet(onUndoReminder)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NeedAvatar(card: CatchUpCard) {
    val colors = MaterialTheme.colorScheme
    val shape = if (card.room.isDirect) MaterialShapes.Cookie9Sided else MaterialShapes.Cookie4Sided
    MatrixAvatar(
        name = card.room.name,
        media = card.room.avatar,
        size = 40.dp,
        shape = shape.toShape(),
        container = colors.onTertiaryContainer,
        content = colors.tertiaryContainer
    )
}

@Composable
private fun IdleActions(kind: NeedKind, onReply: () -> Unit, onRemind: () -> Unit, onDone: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val strong = ButtonDefaults.buttonColors(colors.onTertiaryContainer, colors.tertiaryContainer)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    ) {
        if (kind == NeedKind.Waiting) {
            Button(onClick = onReply, colors = strong) { Text(stringResource(R.string.catch_up_reply)) }
        }
        val remind: @Composable () -> Unit = {
            Icon(Symbols.AlarmOn, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.remind_title), modifier = Modifier.padding(start = 6.dp))
        }
        // A promise has nothing to reply to, so reminding is its main way forward.
        if (kind == NeedKind.Promised) {
            Button(onClick = onRemind, colors = strong) { remind() }
        } else {
            FilledTonalButton(
                onClick = onRemind,
                colors = ButtonDefaults.filledTonalButtonColors(colors.surfaceContainerLowest, colors.onSurface)
            ) { remind() }
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onDone) {
            Icon(Symbols.Check, contentDescription = stringResource(R.string.catch_up_mark_done))
        }
    }
}

@Composable
private fun PresetChips(onPick: (Long) -> Unit, onCancel: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val now = ZonedDateTime.now()
    FlowRow(
        verticalArrangement = Arrangement.Center,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(top = 6.dp)
    ) {
        TimePreset.available(now).forEachIndexed { index, preset ->
            Button(
                onClick = { onPick(preset.at(now)) },
                colors = ButtonDefaults.buttonColors(colors.onTertiaryContainer, colors.tertiaryContainer),
                modifier = Modifier.heightIn(min = 44.dp).riseIn(stagger(index, step = CHIP_STEP))
            ) { Text(stringResource(preset.label)) }
        }
        IconButton(onClick = onCancel) {
            Icon(Symbols.Close, contentDescription = stringResource(R.string.catch_up_cancel_reminder))
        }
    }
}

@Composable
private fun ReminderSet(onUndo: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(top = 8.dp).riseIn(0L)
    ) {
        Icon(Symbols.AlarmOn, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(
            stringResource(R.string.remind_done),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onUndo) { Text(stringResource(R.string.undo)) }
    }
}

private const val CHIP_STEP = 50L
