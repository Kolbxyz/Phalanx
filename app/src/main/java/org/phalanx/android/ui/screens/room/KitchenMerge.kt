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
import org.phalanx.android.R
import org.phalanx.android.data.model.KitchenMix
import org.phalanx.android.data.model.emojiOf
import org.phalanx.android.ui.theme.Symbols
import java.io.File

private val StageSize = 64.dp
private val Spread = 26.dp
private const val TILT_DEGREES = 28f

/**
 * When the draft is exactly two emoji that mix, they slide into each other and pop into their
 * Emoji Kitchen mix above the field; a tap sends the mix in place of the draft.
 */
@Composable
internal fun KitchenMerge(
    draft: String,
    onSend: (KitchenMix) -> Unit,
    kitchen: KitchenViewModel = hiltViewModel()
) {
    val enabled by kitchen.enabled.collectAsStateWithLifecycle()
    val mix by produceState<KitchenMix?>(null, draft, enabled) {
        value = if (enabled) kitchen.mixOfDraft(draft) else null
    }
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
                rotationZ = pop.value * 90f
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
                    scaleX = 1f - merge.value * 0.3f
                    scaleY = 1f - merge.value * 0.3f
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
