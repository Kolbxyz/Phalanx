package org.phalanx.android.ui.screens.catchup

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.ui.components.MatrixAvatar
import org.phalanx.android.ui.components.riseIn
import org.phalanx.android.ui.components.segmentedShape
import org.phalanx.android.ui.components.stagger
import org.phalanx.android.ui.screens.people.sourceOf
import org.phalanx.android.ui.screens.roomlist.label
import org.phalanx.android.ui.theme.Symbols

/**
 * One room in "What happened": who, where and how much is unread, then the model's points once it
 * has read them, with the room a tap away and a way to mark it read without opening it.
 */
@Composable
internal fun HappenedCard(
    card: CatchUpCard,
    index: Int,
    count: Int,
    onOpen: () -> Unit,
    onMarkRead: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    Surface(
        shape = segmentedShape(index, count),
        color = colors.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Header(card)
            AnimatedContent(
                targetState = card.state,
                contentKey = { it::class },
                transitionSpec = {
                    (fadeIn(motion.defaultEffectsSpec()) + expandVertically(motion.defaultEffectsSpec()))
                        .togetherWith(fadeOut(motion.fastEffectsSpec()) + shrinkVertically(motion.fastEffectsSpec()))
                },
                label = "card"
            ) { state ->
                when (state) {
                    CardState.Waiting -> Unit
                    CardState.Reading -> Skeleton()
                    is CardState.Failed -> Text(
                        stringResource(state.message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                    is CardState.Done -> Done(state, card.markedRead, onOpen, onMarkRead)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Header(card: CatchUpCard) {
    val colors = MaterialTheme.colorScheme
    val room = card.room
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MatrixAvatar(
            name = room.name,
            media = room.avatar,
            size = 32.dp,
            shape = (if (room.isDirect) MaterialShapes.Cookie9Sided else MaterialShapes.Cookie4Sided).toShape(),
            container = if (room.isDirect) colors.tertiaryContainer else colors.primaryContainer,
            content = if (room.isDirect) colors.onTertiaryContainer else colors.onPrimaryContainer,
            textStyle = MaterialTheme.typography.labelLarge
        )
        Column(Modifier.weight(1f)) {
            Text(
                room.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val unread = room.unreadCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            Text(
                stringResource(
                    R.string.catch_up_card_meta,
                    stringResource(sourceOf(room).label),
                    pluralStringResource(R.plurals.catch_up_unread, unread, unread)
                ),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant
            )
        }
        when (card.state) {
            CardState.Reading -> {
                val reading = stringResource(R.string.catch_up_reading)
                LoadingIndicator(Modifier.size(24.dp).semantics { contentDescription = reading })
            }
            CardState.Waiting -> Text(
                stringResource(R.string.catch_up_waiting),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant
            )
            else -> Unit
        }
    }
}

/** Two grey lines where the points will be, while the model reads. */
@Composable
private fun Skeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)) {
        SKELETON.forEach { width ->
            Box(
                Modifier
                    .fillMaxWidth(width)
                    .height(10.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(5.dp))
            )
        }
    }
}

@Composable
private fun Done(state: CardState.Done, markedRead: Boolean, onOpen: () -> Unit, onMarkRead: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 10.dp)) {
            state.catchUp.points.forEachIndexed { index, point ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.riseIn(stagger(index, first = POINTS_FIRST, step = POINTS_STEP))
                ) {
                    Box(Modifier.padding(top = 7.dp).size(6.dp).background(colors.primary, CircleShape))
                    Text(point, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onOpen) { Text(stringResource(R.string.catch_up_open_chat)) }
            if (markedRead) {
                Text(
                    stringResource(R.string.catch_up_marked_read),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                TextButton(onClick = { onMarkRead(false) }) { Text(stringResource(R.string.undo)) }
            } else {
                TextButton(onClick = { onMarkRead(true) }) {
                    Icon(Symbols.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.catch_up_mark_read), modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

private val SKELETON = listOf(0.92f, 0.64f)
private const val POINTS_FIRST = 120L
private const val POINTS_STEP = 90L
