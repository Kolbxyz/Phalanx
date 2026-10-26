package org.phalanx.android.ui.screens.room

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import java.io.File
import kotlinx.coroutines.delay
import org.phalanx.android.R
import org.phalanx.android.data.model.KitchenMix
import org.phalanx.android.data.model.emojiOf
import org.phalanx.android.ui.theme.Symbols

private val StageSize = 64.dp
private val Spread = 26.dp
private const val TILT_DEGREES = 28f
private const val CHOICES_DELAY_MILLIS = 600L

// How much smaller the emoji get as they meet, and how far the burst turns as it pops.
private const val MERGE_SHRINK = 0.3f
private const val BURST_TURN_DEGREES = 90f

/**
 * Emoji Kitchen above the composer's field: two typed emoji that mix merge into their mix, and
 * one typed emoji offers a few of its mixes. A tap sends the mix in place of the draft.
 */
@Composable
internal fun KitchenSuggestions(
    draft: String,
    onSend: (KitchenMix) -> Unit,
    kitchen: KitchenViewModel = hiltViewModel()
) {
    val enabled by kitchen.enabled.collectAsStateWithLifecycle()
    val offer by produceState<DraftMixes?>(null, draft, enabled) {
        // One emoji is often the start of a message: its mixes, each a download, wait for a pause.
        if (emojiIn(draft)?.size == 1) delay(CHOICES_DELAY_MILLIS)
        value = if (enabled) kitchen.mixesOfDraft(draft) else null
    }
    KitchenMerge((offer as? DraftMixes.Merge)?.mix, onSend, kitchen)
    KitchenChoices((offer as? DraftMixes.Choices)?.mixes, onSend, kitchen)
}

/** [mix]'s two emoji slide into each other and pop into it; null hides the pill. */
@Composable
private fun KitchenMerge(mix: KitchenMix?, onSend: (KitchenMix) -> Unit, kitchen: KitchenViewModel) {
    // Shown once the image is there, so the animation never plays onto an empty stage.
    val loaded by produceState<Pair<KitchenMix, File>?>(null, mix) {
        value = mix?.let { m -> kitchen.image(m)?.let { m to it } }
    }
    // The last pair stays drawn while the row collapses, so it doesn't blank out.
    var shown by remember { mutableStateOf<Pair<KitchenMix, File>?>(null) }
    val current = loaded?.takeIf { it.first == mix }
    if (current != null) shown = current
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = current != null,
        enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
        exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.defaultEffectsSpec())
    ) {
        val (target, file) = shown ?: return@AnimatedVisibility
        Surface(
            onClick = { onSend(target) },
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp)
        ) {
            Row(
                modifier = Modifier.padding(start = 8.dp, end = 20.dp, top = 6.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MergeStage(target, file)
                Text(stringResource(R.string.composer_kitchen_send), style = MaterialTheme.typography.labelLarge)
                Icon(Symbols.Send, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** The two emoji converge and tilt in, then a burst pops behind the mix as it springs to size. */
@Composable
private fun MergeStage(mix: KitchenMix, file: File) {
    val motion = MaterialTheme.motionScheme
    val merge = remember(mix) { Animatable(0f) }
    val pop = remember(mix) { Animatable(0f) }
    LaunchedEffect(mix) {
        merge.animateTo(1f, motion.slowSpatialSpec())
        pop.animateTo(1f, motion.fastSpatialSpec())
    }
    val spread = with(LocalDensity.current) { Spread.toPx() }
    val burstShape = MaterialShapes.SoftBurst.toShape()
    Box(Modifier.size(StageSize), contentAlignment = Alignment.Center) {
        Surface(
            shape = burstShape,
            color = MaterialTheme.colorScheme.tertiaryContainer,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
                rotationZ = pop.value * BURST_TURN_DEGREES
            }
        ) {}
        listOf(mix.left to -1f, mix.right to 1f).forEach { (codepoints, side) ->
            Text(
                emojiOf(codepoints),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.graphicsLayer {
                    val apart = 1f - merge.value
                    translationX = side * spread * apart
                    rotationZ = side * TILT_DEGREES * apart
                    scaleX = 1f - merge.value * MERGE_SHRINK
                    scaleY = 1f - merge.value * MERGE_SHRINK
                    alpha = (1f - pop.value).coerceIn(0f, 1f)
                }
            )
        }
        AsyncImage(
            model = file,
            contentDescription = mix.body,
            modifier = Modifier.size(StageSize - 12.dp).graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
                rotationZ = (1f - pop.value) * -TILT_DEGREES
                alpha = pop.value.coerceIn(0f, 1f)
            }
        )
    }
}
