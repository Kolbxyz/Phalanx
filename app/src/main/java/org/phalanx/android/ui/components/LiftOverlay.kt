package org.phalanx.android.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * A long-press menu that lifts what was pressed out of the screen: a scrim dims everything, the
 * [stage] floats near the top with a closer look at the item, and the [sheet] of choices rises
 * from the bottom where the thumb is. The caller hides the original in place while [item] is set,
 * so the item reads as picked up rather than copied. The last item stays drawn while it all leaves.
 */
@Composable
internal fun <T : Any> LiftOverlay(
    item: T?,
    onDismiss: () -> Unit,
    closeLabel: String,
    stage: @Composable ColumnScope.(T) -> Unit,
    sheet: @Composable ColumnScope.(T) -> Unit
) {
    var last by remember { mutableStateOf(item) }
    if (item != null) last = item
    val shown = last ?: return
    val visible = item != null
    val motion = MaterialTheme.motionScheme
    BackHandler(enabled = visible, onBack = onDismiss)
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible,
            enter = fadeIn(motion.defaultEffectsSpec()),
            exit = fadeOut(motion.fastEffectsSpec())
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(scrimColor())
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
                    .semantics { contentDescription = closeLabel }
            )
        }
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
            ) {
                // Qualified: inside the Column, the ColumnScope overload would otherwise be picked.
                androidx.compose.animation.AnimatedVisibility(
                    visible,
                    enter = fadeIn(motion.fastEffectsSpec()) +
                        slideInVertically(motion.defaultSpatialSpec()) { it / STAGE_RISE_DIVISOR } +
                        scaleIn(motion.defaultSpatialSpec(), initialScale = STAGE_FROM, transformOrigin = TopCenter),
                    exit = fadeOut(motion.fastEffectsSpec()) +
                        scaleOut(motion.fastSpatialSpec(), targetScale = STAGE_FROM, transformOrigin = TopCenter)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { stage(shown) }
                }
            }
            AnimatedVisibility(
                visible,
                enter = slideInVertically(motion.defaultSpatialSpec()) { it },
                exit = slideOutVertically(motion.fastSpatialSpec()) { it }
            ) {
                LiftSheet(onDismiss) { sheet(shown) }
            }
        }
    }
}

@Composable
private fun LiftSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val threshold = with(LocalDensity.current) { DRAG_TO_CLOSE.toPx() }
    val settle = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    Surface(
        shape = SheetShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = offset.value }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (offset.value > threshold) onDismiss() else scope.launch { offset.animateTo(0f, settle) }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f, settle) } }
                ) { _, delta -> scope.launch { offset.snapTo((offset.value + delta).coerceAtLeast(0f)) } }
            }
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .verticalScroll(rememberScrollState())
                // No side padding: MenuActions pads its own rows, as it does in a ModalSheet.
                .padding(bottom = 16.dp)
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 10.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outline)
            )
            content()
        }
    }
}

/** The scrim behind a lifted item: deeper in the dark theme, where a light one would hardly show. */
@Composable
internal fun scrimColor() = MaterialTheme.colorScheme.scrim.copy(
    alpha = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) DARK_SCRIM else LIGHT_SCRIM
)

private val SheetShape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)
private val TopCenter = TransformOrigin(0.5f, 0f)
private val DRAG_TO_CLOSE = 96.dp
private const val STAGE_FROM = 0.9f

// The stage rises a quarter of its own height as it scales up.
private const val STAGE_RISE_DIVISOR = 4
private const val LIGHT_SCRIM = 0.5f
private const val DARK_SCRIM = 0.62f
