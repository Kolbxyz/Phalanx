package org.phalanx.android.ui.screens.catchup

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.ui.theme.LocalReducedMotion

/** How far the catch-up has got: a count of the chats read, and one segment per chat. */
@Composable
internal fun CatchUpProgress(cards: List<CatchUpCard>, modifier: Modifier = Modifier) {
    val total = cards.size
    val read = cards.count { it.state is CardState.Done || it.state is CardState.Failed }
    val motion = MaterialTheme.motionScheme
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AnimatedContent(
            targetState = read,
            transitionSpec = {
                (fadeIn(motion.fastEffectsSpec()) + scaleIn(motion.fastSpatialSpec(), initialScale = COUNT_FROM))
                    .togetherWith(fadeOut(motion.fastEffectsSpec()))
            },
            label = "read"
        ) { shown ->
            Text(
                text = if (shown == total) {
                    pluralStringResource(R.plurals.catch_up_all_read, total, total)
                } else {
                    pluralStringResource(R.plurals.catch_up_progress, total, shown, total)
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            cards.forEach { card -> Segment(card.state, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun Segment(state: CardState, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val filled = state !is CardState.Waiting
    val color by animateColorAsState(
        if (filled) colors.primary else colors.surfaceContainerHighest,
        MaterialTheme.motionScheme.slowEffectsSpec()
    )
    // The room being read breathes while the model works on it; nothing moves once it is read.
    val alpha = if (state == CardState.Reading && !LocalReducedMotion.current) {
        val pulse = rememberInfiniteTransition(label = "reading")
        val value by pulse.animateFloat(
            initialValue = 1f,
            targetValue = PULSE_LOW,
            animationSpec = infiniteRepeatable(tween(PULSE_MILLIS), RepeatMode.Reverse),
            label = "pulse"
        )
        value
    } else {
        1f
    }
    Box(
        modifier
            .height(8.dp)
            .graphicsLayer { this.alpha = alpha }
            .background(color, RoundedCornerShape(4.dp))
    )
}

private const val COUNT_FROM = 0.86f
private const val PULSE_LOW = 0.4f
private const val PULSE_MILLIS = 700
